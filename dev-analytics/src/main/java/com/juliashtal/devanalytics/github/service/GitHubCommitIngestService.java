package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.juliashtal.devanalytics.helper.ParsingHelper.resolveApiBase;

/**
 * Phase A of two-phase commit ingestion.
 *
 * <p>Walks the GitHub {@code /commits} list endpoint page by page and saves new
 * commits with {@code statsStatus=PENDING}. No per-commit detail calls are made
 * here — that is the responsibility of {@link GitHubCommitStatsEnrichmentService}.</p>
 *
 * <p>The method returns the list of newly saved entities so that the caller can
 * immediately enrich the most recent ones before handing the rest to the
 * background scheduler.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubCommitIngestService {

    /** Page size for the commit list endpoint. GitHub maximum is 100. */
    private static final int PAGE_SIZE = 100;

    /** Batch size for DB saves during ingest (avoids huge single INSERT). */
    private static final int SAVE_BATCH_SIZE = 500;

    /** Pause between page requests to avoid secondary rate limits. */
    private static final long PAGE_PAUSE_MS = 200;

    private static final Pattern LAST_PAGE_PATTERN =
            Pattern.compile("[?&]page=(\\d+)>; rel=\"last\"");

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final GitRepositoryEntityRepository repoRepository;
    private final GitCommitEntityRepository commitRepository;
    private final GitHubClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    /**
     * Ingests new commits for a repository from the GitHub list endpoint.
     *
     * <p>Commits are saved immediately with {@code statsStatus=PENDING}; no stats
     * are fetched here. The caller should pass the returned list to
     * {@link GitHubCommitStatsEnrichmentService#enrichImmediate} for the priority
     * window.</p>
     *
     * @return the newly saved commit entities, newest-first
     */
    @Transactional
    public IngestResult ingestForRepository(Long gitRepoId, SyncJobTracker.JobState jobState) {
        GitRepositoryEntity repo = repoRepository.findById(gitRepoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + gitRepoId));

        DataSourceConfig cfg = repo.getDataSourceConfig();
        String apiBase = resolveApiBase(cfg.getBaseUrl());
        String token = clientFactory.getDecryptedToken(cfg);
        String lastFetched = repo.getLastFetchedCommitHash();

        Set<String> existingHashes = new HashSet<>(
                commitRepository.findHashesByRepositoryId(repo.getId()));

        if (lastFetched == null && jobState != null) {
            int estimatedCount = fetchTotalCommitCount(apiBase, token, repo.getName());
            jobState.phaseTotal = estimatedCount;
            log.info("Estimated commit count for {}: {} (page-count heuristic)",
                    repo.getName(), estimatedCount);
        }

        try {
            NewCommitNodes newCommits = fetchNewCommitNodes(apiBase, token, repo, lastFetched, existingHashes, jobState);
            List<GitCommitEntity> saved = saveAsPending(newCommits.nodes(), repo);

            if (newCommits.newestHash() != null && !newCommits.newestHash().equals(lastFetched)) {
                repo.setLastFetchedCommitHash(newCommits.newestHash());
            }
            repo.setLastScanAt(Instant.now());
            cfg.setLastSuccessSync(Instant.now());
            repoRepository.save(repo);

            log.info("Ingested {} new commits for {} (all PENDING, stats to be enriched)",
                    saved.size(), repo.getName());
            return new IngestResult(saved, apiBase, token);

        } catch (IOException e) {
            throw new GitHubException("Failed to ingest GitHub commits for " + repo.getName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("Interrupted while ingesting GitHub commits for " + repo.getName(), e);
        }
    }

    /**
     * Pages through the GitHub commits list endpoint, collecting nodes for commits not yet in
     * {@code existingHashes}, until {@code lastFetched} is encountered or pages run out.
     * Pauses {@link #PAGE_PAUSE_MS} between page requests to avoid secondary rate limits.
     */
    private NewCommitNodes fetchNewCommitNodes(String apiBase, String token, GitRepositoryEntity repo,
                                                String lastFetched, Set<String> existingHashes,
                                                SyncJobTracker.JobState jobState)
            throws IOException, InterruptedException {
        List<JsonNode> newNodes = new ArrayList<>();
        String newestHash = null;
        boolean done = false;
        int page = 1;

        while (!done) {
            String url = apiBase + "/repos/" + repo.getName()
                    + "/commits?per_page=" + PAGE_SIZE + "&page=" + page;

            HttpResponse<String> response = sendWithRateLimitRetry(buildRequest(url, token), repo.getName());

            if (response.statusCode() != 200) {
                throw new GitHubException("GitHub API returned " + response.statusCode()
                        + " fetching commits for " + repo.getName());
            }

            JsonNode commits = objectMapper.readTree(response.body());
            if (!commits.isArray() || commits.isEmpty()) break;

            for (JsonNode node : commits) {
                String hash = node.path("sha").asText();

                if (lastFetched != null && lastFetched.equals(hash)) {
                    done = true;
                    break;
                }

                if (jobState != null) {
                    jobState.phaseProcessed.incrementAndGet();
                    jobState.totalProcessed.incrementAndGet();
                }

                if (existingHashes.contains(hash)) continue;

                if (newestHash == null) newestHash = hash;
                newNodes.add(node);
            }

            String linkHeader = response.headers().firstValue("Link").orElse("");
            if (!linkHeader.contains("rel=\"next\"")) done = true;
            page++;

            if (!done) Thread.sleep(PAGE_PAUSE_MS);
        }

        return new NewCommitNodes(newNodes, newestHash);
    }

    /**
     * Carries the new commit JSON nodes found by {@link #fetchNewCommitNodes} plus the
     * newest commit hash among them (or {@code null} if none were new).
     */
    private record NewCommitNodes(List<JsonNode> nodes, String newestHash) {}

    /**
     * Builds and saves commit entities with {@code statsStatus=PENDING}.
     * Stats fields are left at their default (0) and will be filled during enrichment.
     */
    private List<GitCommitEntity> saveAsPending(List<JsonNode> nodes, GitRepositoryEntity repo) {
        if (nodes.isEmpty()) return Collections.emptyList();

        List<GitCommitEntity> entities = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            entities.add(buildPendingEntity(node, repo));
        }

        List<GitCommitEntity> result = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i += SAVE_BATCH_SIZE) {
            List<GitCommitEntity> batch = entities.subList(i, Math.min(i + SAVE_BATCH_SIZE, entities.size()));
            result.addAll(commitRepository.saveAll(batch));
        }
        return result;
    }

    private GitCommitEntity buildPendingEntity(JsonNode node, GitRepositoryEntity repo) {
        GitCommitEntity entity = new GitCommitEntity();
        entity.setRepository(repo);
        entity.setHash(node.path("sha").asText());

        JsonNode commit = node.path("commit");
        JsonNode author = commit.path("author");
        entity.setAuthorName(author.path("name").asText("unknown"));
        entity.setAuthorEmail(author.path("email").asText("unknown"));

        // `commit.author` above is raw Git metadata: whatever the committer configured locally,
        // and the only thing this method used to read. The top-level `author` is GitHub's own
        // resolution of that email to an account, and is the stable attribution key. It is null
        // when the email belongs to no GitHub account, so both fields stay null in that case.
        JsonNode githubAuthor = node.path("author");
        if (githubAuthor.isObject()) {
            entity.setAuthorGithubId(githubAuthor.path("id").isNumber()
                    ? githubAuthor.path("id").asLong() : null);
            entity.setAuthorGithubLogin(githubAuthor.path("login").asText(null));
        }

        String dateStr = author.path("date").asText(null);
        entity.setAuthorDate(dateStr != null ? Instant.parse(dateStr) : Instant.now());
        entity.setMessage(commit.path("message").asText(null));

        JsonNode parents = node.path("parents");
        if (parents.isArray() && !parents.isEmpty()) {
            entity.setParentHash(parents.get(0).path("sha").asText(null));
        }

        // Stats are intentionally not set here; they default to 0 until enriched.
        entity.setStatsStatus(StatsStatus.PENDING);

        return entity;
    }

    private HttpResponse<String> sendWithRateLimitRetry(HttpRequest request, String context)
            throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 403 || response.statusCode() == 429) {
            long waitSeconds = response.headers().firstValue("Retry-After")
                    .map(v -> { try { return Long.parseLong(v); } catch (NumberFormatException e) { return 60L; } })
                    .orElse(60L);
            log.warn("GitHub rate limit hit ({}) during ingest, waiting {}s for {}",
                    response.statusCode(), waitSeconds, context);
            Thread.sleep(waitSeconds * 1_000L);
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        }
        return response;
    }

    private static HttpRequest buildRequest(String url, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    private int fetchTotalCommitCount(String apiBase, String token, String repoFullName) {
        try {
            HttpRequest request = buildRequest(
                    apiBase + "/repos/" + repoFullName + "/commits?per_page=1", token);
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());

            String linkHeader = response.headers().firstValue("Link").orElse("");
            Matcher m = LAST_PAGE_PATTERN.matcher(linkHeader);
            // With per_page=1 the last page number equals the total commit count exactly.
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            log.debug("Could not estimate commit count for {}: {}", repoFullName, e.getMessage());
        }
        return -1;
    }

    /**
     * Carries the result of an ingest run: the saved entities plus the HTTP
     * coordinates needed by the enrichment service.
     */
    public record IngestResult(
            List<GitCommitEntity> savedEntities,
            String apiBase,
            String token
    ) {}
}
