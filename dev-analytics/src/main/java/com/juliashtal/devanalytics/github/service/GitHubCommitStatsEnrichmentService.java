package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
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
 * Phase B/C of two-phase commit ingestion: enriches PENDING commits with
 * per-commit stats (additions/deletions/filesChanged) fetched from the GitHub
 * single-commit detail endpoint.
 *
 * <h3>Two entry points</h3>
 * <ul>
 *   <li>{@link #enrichImmediate} — called synchronously right after ingest for the
 *       most recent commits (priority window, e.g. last 150). Uses a small thread
 *       pool (2 workers) with rate limiting so the initial sync completes quickly
 *       without hammering GitHub.</li>
 *   <li>{@link #processPendingBatchForRepo} — called by {@link CommitStatsEnrichmentScheduler}
 *       every 2 minutes for background backfill. Processes up to 50 PENDING commits
 *       per run in a single thread at ~1 req/sec.</li>
 * </ul>
 *
 * <h3>Rate limiting</h3>
 * A simple synchronized token-bucket approach caps throughput at ~1.4 req/sec
 * globally. A random jitter of 100–300 ms is added per request. Exponential
 * backoff is applied on 403/429/5xx responses up to {@value MAX_BACKOFF_MS} ms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubCommitStatsEnrichmentService {

    /** Max commits enriched synchronously right after ingest (priority window). */
    static final int IMMEDIATE_ENRICH_LIMIT = 150;

    /** Max commits per background batch run. */
    private static final int BATCH_SIZE = 50;

    /** Max enrichment attempts before a commit is marked FAILED. */
    private static final int MAX_ATTEMPTS = 3;

    /** Minimum interval between detail requests across all workers (nanoseconds). */
    private static final long MIN_INTERVAL_NS = 700_000_000L; // ~1.4 req/sec

    /** Initial backoff on rate-limit response (ms). */
    private static final long INITIAL_BACKOFF_MS = 5_000L;
    private static final long MAX_BACKOFF_MS = 120_000L;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final GitCommitEntityRepository commitRepository;
    private final ObjectMapper objectMapper;

    /** Shared rate-limit state across all threads in this JVM. */
    private long lastRequestTimeNs = 0;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Enriches the given commits immediately using up to 2 parallel workers.
     *
     * <p>Typically called right after ingest with the most recent {@value IMMEDIATE_ENRICH_LIMIT}
     * commits. This returns when all submitted tasks finish or when interrupted.</p>
     *
     * @param commits     entities saved by the ingest service (must have DB IDs)
     * @param apiBase     resolved GitHub API base URL
     * @param token       plaintext GitHub PAT
     * @param repoFullName "owner/repo" string
     */
    public void enrichImmediate(List<GitCommitEntity> commits, String apiBase,
                                String token, String repoFullName) {
        if (commits.isEmpty()) return;

        List<GitCommitEntity> priority = commits.subList(0, Math.min(IMMEDIATE_ENRICH_LIMIT, commits.size()));
        log.info("Immediately enriching {} commits for {}", priority.size(), repoFullName);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>(priority.size());

        for (GitCommitEntity commit : priority) {
            futures.add(pool.submit(() -> enrichSingle(commit, apiBase, token, repoFullName)));
        }
        pool.shutdown();

        try {
            pool.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted waiting for immediate enrichment of {}", repoFullName);
            pool.shutdownNow();
        }

        // Log any suppressed exceptions from workers.
        for (Future<?> f : futures) {
            if (f.isDone()) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    log.warn("Enrichment worker error: {}", e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Background enrichment for a single repository. Used by the scheduler which
     * iterates per-repo and provides the already-decrypted token.
     *
     * @return number of commits processed in this run
     */
    @Transactional
    public int processPendingBatchForRepo(String apiBase, String decryptedToken,
                                          String repoFullName, Long repositoryId) {
        List<GitCommitEntity> batch = commitRepository.findPendingCommitsForRepository(
                StatsStatus.PENDING, repositoryId, PageRequest.of(0, BATCH_SIZE));

        if (batch.isEmpty()) return 0;

        log.info("Background enrichment for {}: processing {} PENDING commits",
                repoFullName, batch.size());

        for (GitCommitEntity commit : batch) {
            enrichSingle(commit, apiBase, decryptedToken, repoFullName);
        }

        commitRepository.saveAll(batch);
        log.info("Background enrichment for {}: {} commits processed", repoFullName, batch.size());
        return batch.size();
    }

    // -------------------------------------------------------------------------
    // Core enrichment logic
    // -------------------------------------------------------------------------

    /**
     * Fetches stats for one commit and updates the entity in place.
     * Does NOT save — the caller is responsible for batching the saves.
     */
    void enrichSingle(GitCommitEntity commit, String apiBase, String token, String repoFullName) {
        commit.setStatsAttempts(commit.getStatsAttempts() + 1);

        if (commit.getStatsAttempts() > MAX_ATTEMPTS) {
            log.warn("Giving up on commit {}/{}: too many failed attempts", repoFullName, commit.getHash());
            commit.setStatsStatus(StatsStatus.FAILED);
            commit.setStatsFetchedAt(Instant.now());
            return;
        }

        try {
            waitForRateLimit();
            addJitter();

            String url = apiBase + "/repos/" + repoFullName + "/commits/" + commit.getHash();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> response = sendWithBackoff(request, repoFullName, commit.getHash());
            applyStats(commit, response, repoFullName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted enriching commit {}/{}", repoFullName, commit.getHash());
        } catch (IOException e) {
            log.warn("IO error enriching commit {}/{}: {}", repoFullName, commit.getHash(), e.getMessage());
            // Leave as PENDING; will be retried in the next batch.
        }
    }

    private void applyStats(GitCommitEntity commit, HttpResponse<String> response,
                             String repoFullName) throws IOException {
        int status = response.statusCode();

        if (status == 200) {
            JsonNode detail = objectMapper.readTree(response.body());
            JsonNode stats = detail.path("stats");
            commit.setAdditions(stats.path("additions").asInt(0));
            commit.setDeletions(stats.path("deletions").asInt(0));
            JsonNode files = detail.path("files");
            commit.setFilesChanged(files.isArray() ? files.size() : 0);
            commit.setStatsStatus(StatsStatus.COMPLETE);
            commit.setStatsFetchedAt(Instant.now());
            return;
        }

        if (status == 403) {
            // Distinguish "diff too large" (no Retry-After, body mentions "too large")
            // from a secondary rate limit (body mentions "rate limit" or "secondary").
            String body = response.body() != null ? response.body().toLowerCase() : "";
            if (body.contains("too large") || body.contains("maximum")) {
                log.warn("Diff too large for {}/{}, marking SKIPPED", repoFullName, commit.getHash());
                commit.setStatsStatus(StatsStatus.SKIPPED);
                commit.setStatsFetchedAt(Instant.now());
            } else {
                // Rate limit — leave as PENDING for retry.
                log.warn("Secondary rate limit for {}/{}, will retry", repoFullName, commit.getHash());
            }
            return;
        }

        if (status == 422 || status == 404) {
            // Unprocessable or not found — no point retrying.
            log.warn("Unprocessable/not-found ({}) for {}/{}, marking SKIPPED",
                    status, repoFullName, commit.getHash());
            commit.setStatsStatus(StatsStatus.SKIPPED);
            commit.setStatsFetchedAt(Instant.now());
            return;
        }

        log.warn("Unexpected status {} enriching {}/{}", status, repoFullName, commit.getHash());
        // Leave as PENDING for retry.
    }

    // -------------------------------------------------------------------------
    // Rate limiting & HTTP helpers
    // -------------------------------------------------------------------------

    /**
     * Blocks until enough time has passed since the last request to respect the
     * global rate limit. Synchronized so multiple threads share one counter.
     */
    private synchronized void waitForRateLimit() throws InterruptedException {
        long now = System.nanoTime();
        long elapsed = now - lastRequestTimeNs;
        if (elapsed < MIN_INTERVAL_NS) {
            long sleepMs = (MIN_INTERVAL_NS - elapsed) / 1_000_000L;
            Thread.sleep(sleepMs);
        }
        lastRequestTimeNs = System.nanoTime();
    }

    /** Adds 100–300 ms random jitter to reduce bursty patterns. */
    private static void addJitter() throws InterruptedException {
        Thread.sleep(100 + (long) (Math.random() * 200));
    }

    /**
     * Sends a request with exponential backoff on 403/429/5xx responses.
     * Checks {@code X-RateLimit-Remaining} and {@code X-RateLimit-Reset} headers
     * for proactive throttling rather than only reacting after being blocked.
     */
    private HttpResponse<String> sendWithBackoff(HttpRequest request, String repoFullName, String sha)
            throws IOException, InterruptedException {

        long backoffMs = INITIAL_BACKOFF_MS;
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        // Proactive throttle: if fewer than 100 requests remain, pause until reset.
        String remaining = response.headers().firstValue("X-RateLimit-Remaining").orElse(null);
        String resetAt = response.headers().firstValue("X-RateLimit-Reset").orElse(null);
        if (remaining != null && Integer.parseInt(remaining) < 100 && resetAt != null) {
            long resetEpoch = Long.parseLong(resetAt);
            long waitMs = Math.max(0, (resetEpoch * 1_000L) - System.currentTimeMillis());
            if (waitMs > 0) {
                log.warn("Rate limit nearly exhausted ({} remaining), pausing {}s",
                        remaining, waitMs / 1000);
                Thread.sleep(Math.min(waitMs, MAX_BACKOFF_MS));
                response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            }
        }

        // Reactive backoff on 429/403 (rate limited) / 5xx.
        int status = response.statusCode();
        if (status == 429 || status == 500 || status == 502 || status == 503) {
            String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            long waitMs = retryAfter != null ? Long.parseLong(retryAfter) * 1_000L : backoffMs;
            log.warn("GitHub {} for {}/{}, backing off {}ms", status, repoFullName, sha, waitMs);
            Thread.sleep(Math.min(waitMs, MAX_BACKOFF_MS));
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        }

        return response;
    }
}
