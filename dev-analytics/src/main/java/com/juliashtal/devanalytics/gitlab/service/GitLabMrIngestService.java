package com.juliashtal.devanalytics.gitlab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.exception.GitLabException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Ingests GitLab Merge Requests into the {@code github_pull_requests} table
 * (reusing the same entity). Stats (additions/deletions/changedFiles) are not
 * available from the list endpoint and are left as 0 with
 * {@code statsStatus=COMPLETE}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabMrIngestService {

    private static final int PAGE_SIZE = 100;
    private static final long PAGE_PAUSE_MS = 200;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final GitRepositoryEntityRepository repoRepository;
    private final GitHubPullRequestRepository prRepository;
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

        Map<Integer, GitHubPullRequestEntity> existing = prRepository.findByRepository(repo)
                .stream().collect(Collectors.toMap(GitHubPullRequestEntity::getNumber, p -> p));

        int saved = 0;
        int page = 1;

        try {
            while (true) {
                String url = apiBase + "/projects/" + encodedPath
                        + "/merge_requests?state=all&order_by=updated_at&sort=desc&per_page=" + PAGE_SIZE + "&page=" + page;

                HttpResponse<String> response = HTTP_CLIENT.send(
                        clientFactory.buildRequest(url, token),
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429) {
                    long waitSeconds = response.headers().firstValue("Retry-After")
                            .map(v -> { try { return Long.parseLong(v); } catch (NumberFormatException e) { return 60L; } })
                            .orElse(60L);
                    log.warn("GitLab rate limit hit during MR ingest, waiting {}s for {}", waitSeconds, repo.getName());
                    Thread.sleep(waitSeconds * 1_000L);
                    response = HTTP_CLIENT.send(clientFactory.buildRequest(url, token), HttpResponse.BodyHandlers.ofString());
                }

                if (response.statusCode() != 200) {
                    throw new GitLabException("GitLab API returned " + response.statusCode()
                            + " fetching MRs for " + repo.getName());
                }

                JsonNode mrs = objectMapper.readTree(response.body());
                if (!mrs.isArray() || mrs.isEmpty()) break;

                for (JsonNode node : mrs) {
                    int iid = node.path("iid").asInt();
                    Instant updatedAt = parseDate(node.path("updated_at").asText(null));

                    GitHubPullRequestEntity entity = existing.get(iid);
                    if (entity != null && entity.getUpdatedAt() != null
                            && entity.getUpdatedAt().equals(updatedAt)) {
                        // Unchanged — skip
                        continue;
                    }

                    if (entity == null) entity = new GitHubPullRequestEntity();
                    entity.setRepository(repo);
                    entity.setNumber(iid);
                    entity.setTitle(node.path("title").asText(""));
                    entity.setAuthorLogin(node.path("author").path("username").asText(null));

                    String state = node.path("state").asText("opened");
                    boolean merged = "merged".equals(state);
                    entity.setState(merged || "closed".equals(state) ? "closed" : "open");
                    entity.setMerged(merged);
                    entity.setCreatedAt(parseDate(node.path("created_at").asText(null)));
                    entity.setUpdatedAt(updatedAt);
                    entity.setMergedAt(parseDate(node.path("merged_at").asText(null)));
                    entity.setClosedAt(parseDate(node.path("closed_at").asText(null)));

                    // Lead time: merge - create, in hours
                    if (merged && entity.getMergedAt() != null && entity.getCreatedAt() != null) {
                        long hours = (entity.getMergedAt().toEpochMilli() - entity.getCreatedAt().toEpochMilli()) / 3_600_000L;
                        entity.setLeadTimeHours(hours);
                    }

                    entity.setStatsStatus(StatsStatus.COMPLETE);

                    if (jobState != null) {
                        jobState.phaseProcessed.incrementAndGet();
                        jobState.totalProcessed.incrementAndGet();
                    }

                    prRepository.save(entity);
                    saved++;
                }

                String nextPage = response.headers().firstValue("X-Next-Page").orElse("").strip();
                if (nextPage.isBlank()) break;
                page++;

                Thread.sleep(PAGE_PAUSE_MS);
            }

            cfg.setLastSuccessSync(LocalDateTime.now());
            log.info("GitLab: ingested {} MRs for {}", saved, repo.getName());
            return saved;

        } catch (GitLabException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitLabException("Interrupted while ingesting GitLab MRs for " + repo.getName(), e);
        } catch (Exception e) {
            throw new GitLabException("Failed to ingest GitLab MRs for " + repo.getName(), e);
        }
    }

    private static Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank() || "null".equals(dateStr)) return null;
        try {
            return OffsetDateTime.parse(dateStr).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}
