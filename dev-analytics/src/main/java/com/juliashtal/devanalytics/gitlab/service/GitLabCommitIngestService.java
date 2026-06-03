package com.juliashtal.devanalytics.gitlab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.exception.GitLabException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Ingests commits from the GitLab repository commits API.
 *
 * <p>Uses {@code with_stats=true} to retrieve additions/deletions inline, so
 * commits are saved with {@code statsStatus=COMPLETE} in a single pass — no
 * separate enrichment step is required.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabCommitIngestService {

    private static final int PAGE_SIZE = 100;
    private static final int SAVE_BATCH_SIZE = 500;
    private static final long PAGE_PAUSE_MS = 200;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final GitRepositoryEntityRepository repoRepository;
    private final GitCommitEntityRepository commitRepository;
    private final GitLabClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    @Transactional
    public int ingestForRepository(Long gitRepoId, SyncJobTracker.JobState jobState) {
        GitRepositoryEntity repo = repoRepository.findById(gitRepoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + gitRepoId));

        DataSourceConfig cfg = repo.getDataSourceConfig();
        String apiBase = clientFactory.resolveApiBase(cfg);
        String token = clientFactory.getDecryptedToken(cfg);
        String encodedPath = URLEncoder.encode(repo.getName(), StandardCharsets.UTF_8);
        String lastFetched = repo.getLastFetchedCommitHash();

        Set<String> existingHashes = new HashSet<>(commitRepository.findHashesByRepositoryId(repo.getId()));

        try {
            List<JsonNode> newNodes = new ArrayList<>();
            String newestHash = null;
            boolean done = false;
            int page = 1;

            while (!done) {
                String url = apiBase + "/projects/" + encodedPath
                        + "/repository/commits?with_stats=true&per_page=" + PAGE_SIZE + "&page=" + page;

                HttpResponse<String> response = HTTP_CLIENT.send(
                        clientFactory.buildRequest(url, token),
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429) {
                    long waitSeconds = response.headers().firstValue("Retry-After")
                            .map(v -> { try { return Long.parseLong(v); } catch (NumberFormatException e) { return 60L; } })
                            .orElse(60L);
                    log.warn("GitLab rate limit hit during commit ingest, waiting {}s for {}", waitSeconds, repo.getName());
                    Thread.sleep(waitSeconds * 1_000L);
                    response = HTTP_CLIENT.send(clientFactory.buildRequest(url, token), HttpResponse.BodyHandlers.ofString());
                }

                if (response.statusCode() != 200) {
                    throw new GitLabException("GitLab API returned " + response.statusCode()
                            + " fetching commits for " + repo.getName());
                }

                JsonNode commits = objectMapper.readTree(response.body());
                if (!commits.isArray() || commits.isEmpty()) break;

                for (JsonNode node : commits) {
                    String hash = node.path("id").asText();

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

                String nextPage = response.headers().firstValue("X-Next-Page").orElse("").strip();
                if (nextPage.isBlank()) done = true;
                page++;

                if (!done) Thread.sleep(PAGE_PAUSE_MS);
            }

            List<GitCommitEntity> saved = saveAsComplete(newNodes, repo);

            if (newestHash != null && !newestHash.equals(lastFetched)) {
                repo.setLastFetchedCommitHash(newestHash);
            }
            repo.setLastScanAt(LocalDateTime.now());
            cfg.setLastSuccessSync(LocalDateTime.now());
            repoRepository.save(repo);

            log.info("GitLab: ingested {} new commits for {}", saved.size(), repo.getName());
            return saved.size();

        } catch (GitLabException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitLabException("Interrupted while ingesting GitLab commits for " + repo.getName(), e);
        } catch (Exception e) {
            throw new GitLabException("Failed to ingest GitLab commits for " + repo.getName(), e);
        }
    }

    private List<GitCommitEntity> saveAsComplete(List<JsonNode> nodes, GitRepositoryEntity repo) {
        if (nodes.isEmpty()) return Collections.emptyList();

        List<GitCommitEntity> entities = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            entities.add(buildCompleteEntity(node, repo));
        }

        List<GitCommitEntity> result = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i += SAVE_BATCH_SIZE) {
            List<GitCommitEntity> batch = entities.subList(i, Math.min(i + SAVE_BATCH_SIZE, entities.size()));
            result.addAll(commitRepository.saveAll(batch));
        }
        return result;
    }

    private GitCommitEntity buildCompleteEntity(JsonNode node, GitRepositoryEntity repo) {
        GitCommitEntity entity = new GitCommitEntity();
        entity.setRepository(repo);
        entity.setHash(node.path("id").asText());
        entity.setAuthorName(node.path("author_name").asText("unknown"));
        entity.setAuthorEmail(node.path("author_email").asText("unknown"));

        String dateStr = node.path("authored_date").asText(null);
        entity.setAuthorDate(parseGitLabDate(dateStr));
        entity.setMessage(node.path("message").asText(null));

        JsonNode parentIds = node.path("parent_ids");
        if (parentIds.isArray() && !parentIds.isEmpty()) {
            entity.setParentHash(parentIds.get(0).asText(null));
        }

        JsonNode stats = node.path("stats");
        entity.setAdditions(stats.path("additions").asInt(0));
        entity.setDeletions(stats.path("deletions").asInt(0));
        // GitLab stats.total = additions + deletions (not file count); filesChanged stays 0
        entity.setFilesChanged(0);

        entity.setStatsStatus(StatsStatus.COMPLETE);
        entity.setStatsFetchedAt(Instant.now());

        return entity;
    }

    private static Instant parseGitLabDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return Instant.now();
        try {
            return OffsetDateTime.parse(dateStr).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
