package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.github.model.GitHubPrReviewEntity;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Enriches {@link GitHubPullRequestEntity} records with size stats
 * (additions/deletions/changedFiles/commitsCount) that are absent from the
 * GitHub PR list endpoint and must be fetched from the single-PR detail endpoint:
 * {@code GET /repos/{owner}/{repo}/pulls/{number}}.
 *
 * <h3>Two entry points</h3>
 * <ul>
 *   <li>{@link #enrichImmediate} — called synchronously right after ingest for the
 *       most recent {@value IMMEDIATE_ENRICH_LIMIT} PRs. Each PR gets its reviews
 *       fetched and its size stats fetched from the detail endpoint. Uses 2 workers
 *       + rate limiting.</li>
 *   <li>{@link #processPendingBatchForRepo} — called by the background scheduler every
 *       2 minutes for older PENDING PRs, 50 per run.</li>
 * </ul>
 *
 * <h3>What enrichment covers</h3>
 * <ul>
 *   <li>Reviews: fetched from {@code /pulls/{number}/reviews} and stored in
 *       {@code GitHubPrReviewEntity}. Existing reviews for the PR are replaced.</li>
 *   <li>Size stats: {@code additions/deletions/changedFiles/commitsCount} from
 *       {@code GET /repos/{owner}/{repo}/pulls/{number}}.</li>
 * </ul>
 *
 * <h3>Re-enrichment policy</h3>
 * <ul>
 *   <li>New PRs: always PENDING — stats not yet fetched.</li>
 *   <li>Updated open PRs: reset to PENDING on each sync — their diff can grow with new commits.</li>
 *   <li>Merged PRs already COMPLETE: never re-fetched — their diff is immutable after merge.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubPrStatsEnrichmentService {

    /** PRs enriched immediately after ingest (priority window — matches commit enricher). */
    static final int IMMEDIATE_ENRICH_LIMIT = 150;

    /** PRs processed per background batch run. */
    private static final int BATCH_SIZE = 50;

    private static final int MAX_ATTEMPTS = 3;

    /** ~1.4 req/sec — same budget as the commit enricher, shared via scheduler sequencing. */
    private static final long MIN_INTERVAL_NS = 700_000_000L;
    private static final long INITIAL_BACKOFF_MS = 5_000L;
    private static final long MAX_BACKOFF_MS = 120_000L;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final GitHubPullRequestRepository prRepository;
    private final GitHubPrReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;

    private long lastRequestTimeNs = 0;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Synchronously enriches the PENDING PRs from a just-saved batch.
     * Runs up to 2 workers in parallel, rate-limited.
     */
    public void enrichImmediate(List<GitHubPullRequestEntity> prs, String apiBase,
                                String token, String repoFullName) {
        List<GitHubPullRequestEntity> pending = prs.stream()
                .filter(p -> p.getStatsStatus() == StatsStatus.PENDING)
                .limit(IMMEDIATE_ENRICH_LIMIT)
                .toList();
        if (pending.isEmpty()) return;

        log.info("Immediately enriching {} PRs for {}", pending.size(), repoFullName);

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(2, pending.size()));
        List<Future<?>> futures = new ArrayList<>(pending.size());

        for (GitHubPullRequestEntity pr : pending) {
            futures.add(pool.submit(() -> enrichSingle(pr, apiBase, token, repoFullName)));
        }
        pool.shutdown();

        try {
            pool.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted waiting for PR enrichment of {}", repoFullName);
            pool.shutdownNow();
        }

        for (Future<?> f : futures) {
            if (f.isDone()) {
                try { f.get(); }
                catch (ExecutionException e) { log.warn("PR enrichment worker error: {}", e.getCause().getMessage()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        prRepository.saveAll(pending);
    }

    /**
     * Background batch: picks up to {@value BATCH_SIZE} PENDING PRs for one repo
     * (newest-first) and enriches them sequentially.
     *
     * @return number of PRs processed
     */
    @Transactional
    public int processPendingBatchForRepo(String apiBase, String decryptedToken,
                                          String repoFullName, Long repositoryId) {
        List<GitHubPullRequestEntity> batch = prRepository.findPendingPrsForRepository(
                StatsStatus.PENDING, repositoryId, PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return 0;

        log.info("Background PR enrichment for {}: processing {} PENDING PRs",
                repoFullName, batch.size());

        for (GitHubPullRequestEntity pr : batch) {
            enrichSingle(pr, apiBase, decryptedToken, repoFullName);
        }

        prRepository.saveAll(batch);
        log.info("Background PR enrichment for {}: {} PRs processed", repoFullName, batch.size());
        return batch.size();
    }

    // -------------------------------------------------------------------------
    // Core enrichment logic
    // -------------------------------------------------------------------------

    void enrichSingle(GitHubPullRequestEntity pr, String apiBase, String token, String repoFullName) {
        pr.setStatsAttempts(pr.getStatsAttempts() + 1);

        if (pr.getStatsAttempts() > MAX_ATTEMPTS) {
            log.warn("Giving up on PR #{} in {}: too many failed attempts", pr.getNumber(), repoFullName);
            pr.setStatsStatus(StatsStatus.FAILED);
            pr.setStatsFetchedAt(Instant.now());
            return;
        }

        try {
            refreshReviews(pr, apiBase, token, repoFullName);

            // Fetch size stats from the PR detail endpoint (rate-limited).
            waitForRateLimit();
            addJitter();

            String url = apiBase + "/repos/" + repoFullName + "/pulls/" + pr.getNumber();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> response = sendWithBackoff(request, repoFullName, pr.getNumber());
            applyStats(pr, response, repoFullName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted enriching PR #{} in {}", pr.getNumber(), repoFullName);
        } catch (IOException e) {
            log.warn("IO error enriching PR #{} in {}: {}", pr.getNumber(), repoFullName, e.getMessage());
        }
    }

    /**
     * Re-fetches one PR's reviews and replaces the stored rows.
     *
     * <p>Extracted verbatim from {@link #enrichSingle}, which still calls it: same fetch, same
     * delete-then-insert, same ordering. Exposed so the attribution backfill can refresh reviews
     * for a PR whose stats are already {@code COMPLETE} — enrichment never revisits those, so
     * the reviewer IDs on historical rows would otherwise stay null forever.
     */
    void refreshReviews(GitHubPullRequestEntity pr, String apiBase, String token, String repoFullName)
            throws IOException, InterruptedException {
        List<GitHubPrReviewEntity> reviews = fetchReviews(pr, apiBase, token, repoFullName);
        reviewRepository.deleteAllByPullRequest(pr);
        if (!reviews.isEmpty()) {
            reviewRepository.saveAll(reviews);
        }
    }

    /**
     * Fetches all reviews for a single PR via raw HTTP (with pagination).
     * Most PRs need only one call.
     */
    private List<GitHubPrReviewEntity> fetchReviews(GitHubPullRequestEntity pr,
                                                      String apiBase, String token,
                                                      String repoFullName)
            throws IOException, InterruptedException {

        List<GitHubPrReviewEntity> reviews = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = apiBase + "/repos/" + repoFullName
                    + "/pulls/" + pr.getNumber() + "/reviews?per_page=100&page=" + page;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) break;

            JsonNode nodes = objectMapper.readTree(response.body());
            if (!nodes.isArray() || nodes.isEmpty()) break;

            for (JsonNode node : nodes) {
                Instant submittedAt = parseInstant(node.path("submitted_at"));
                if (submittedAt == null) continue;

                GitHubPrReviewEntity review = new GitHubPrReviewEntity();
                review.setPullRequest(pr);
                JsonNode reviewer = node.path("user");
                review.setReviewerLogin(reviewer.path("login").asText(null));
                review.setReviewerGithubId(reviewer.isObject() && reviewer.path("id").isNumber()
                        ? reviewer.path("id").asLong() : null);
                review.setState(node.path("state").asText(null));
                review.setSubmittedAt(submittedAt);
                reviews.add(review);
            }

            String linkHeader = response.headers().firstValue("Link").orElse("");
            if (!linkHeader.contains("rel=\"next\"")) break;
            page++;
        }

        return reviews;
    }

    private static Instant parseInstant(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String s = node.asText();
        if (s.isBlank() || s.equals("null")) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private void applyStats(GitHubPullRequestEntity pr, HttpResponse<String> response,
                             String repoFullName) throws IOException {
        int status = response.statusCode();

        if (status == 200) {
            JsonNode detail = objectMapper.readTree(response.body());
            pr.setAdditions(detail.path("additions").asInt(0));
            pr.setDeletions(detail.path("deletions").asInt(0));
            pr.setChangedFiles(detail.path("changed_files").asInt(0));
            pr.setCommitsCount(detail.path("commits").asInt(0));
            pr.setStatsStatus(StatsStatus.COMPLETE);
            pr.setStatsFetchedAt(Instant.now());
            return;
        }

        if (status == 403) {
            String body = response.body() != null ? response.body().toLowerCase() : "";
            if (body.contains("too large") || body.contains("maximum")) {
                log.warn("Diff too large for PR #{} in {}, marking SKIPPED", pr.getNumber(), repoFullName);
                pr.setStatsStatus(StatsStatus.SKIPPED);
                pr.setStatsFetchedAt(Instant.now());
            } else {
                log.warn("Secondary rate limit for PR #{} in {}, will retry", pr.getNumber(), repoFullName);
            }
            return;
        }

        if (status == 404 || status == 422) {
            log.warn("PR #{} not found/unprocessable in {} ({}), marking SKIPPED",
                    pr.getNumber(), repoFullName, status);
            pr.setStatsStatus(StatsStatus.SKIPPED);
            pr.setStatsFetchedAt(Instant.now());
            return;
        }

        log.warn("Unexpected status {} enriching PR #{} in {}", status, pr.getNumber(), repoFullName);
    }

    // -------------------------------------------------------------------------
    // Rate limiting & HTTP helpers  (mirrors GitHubCommitStatsEnrichmentService)
    // -------------------------------------------------------------------------

    private synchronized void waitForRateLimit() throws InterruptedException {
        long elapsed = System.nanoTime() - lastRequestTimeNs;
        if (elapsed < MIN_INTERVAL_NS) {
            Thread.sleep((MIN_INTERVAL_NS - elapsed) / 1_000_000L);
        }
        lastRequestTimeNs = System.nanoTime();
    }

    private static void addJitter() throws InterruptedException {
        Thread.sleep(100 + (long) (Math.random() * 200));
    }

    private HttpResponse<String> sendWithBackoff(HttpRequest request, String repoFullName, int prNumber)
            throws IOException, InterruptedException {

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        // Proactive throttle on low remaining quota.
        String remaining = response.headers().firstValue("X-RateLimit-Remaining").orElse(null);
        String resetAt   = response.headers().firstValue("X-RateLimit-Reset").orElse(null);
        if (remaining != null && Integer.parseInt(remaining) < 100 && resetAt != null) {
            long waitMs = Math.max(0, Long.parseLong(resetAt) * 1_000L - System.currentTimeMillis());
            if (waitMs > 0) {
                log.warn("Rate limit nearly exhausted ({} remaining), pausing {}s",
                        remaining, waitMs / 1000);
                Thread.sleep(Math.min(waitMs, MAX_BACKOFF_MS));
                response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            }
        }

        // Reactive backoff on 429 / 5xx.
        int status = response.statusCode();
        if (status == 429 || status == 500 || status == 502 || status == 503) {
            String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            long waitMs = retryAfter != null ? Long.parseLong(retryAfter) * 1_000L : INITIAL_BACKOFF_MS;
            log.warn("GitHub {} for PR #{} in {}, backing off {}ms", status, prNumber, repoFullName, waitMs);
            Thread.sleep(Math.min(waitMs, MAX_BACKOFF_MS));
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        }

        return response;
    }
}
