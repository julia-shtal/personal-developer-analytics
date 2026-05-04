package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.juliashtal.devanalytics.helper.ParsingHelper.resolveApiBase;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubPullRequestCollector {

    private static final int BATCH_SIZE = 100;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Pattern LAST_PAGE_PATTERN =
            Pattern.compile("[?&]page=(\\d+)>; rel=\"last\"");

    private final GitRepositoryEntityRepository repoRepository;
    private final GitHubPullRequestRepository prRepository;
    private final GitHubClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    /**
     * Phase A of two-phase PR collection: fast ingest from the GitHub list endpoint.
     *
     * <p>Pages through all PRs and saves new or changed ones with
     * {@code statsStatus=PENDING}. No review fetches and no per-PR detail calls are
     * made here — that is the responsibility of {@link GitHubPrStatsEnrichmentService}.</p>
     *
     * <p>Unchanged PRs (same {@code updated_at}) are skipped entirely, so incremental
     * runs only write the rows that actually changed.</p>
     *
     * @return {@link IngestResult} carrying the saved entities plus the HTTP credentials
     *         needed by the enrichment service
     */
    @Transactional
    public IngestResult collectPullRequests(Long gitRepoId, SyncJobTracker.JobState jobState) {
        GitRepositoryEntity repo = repoRepository.findById(gitRepoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + gitRepoId));

        DataSourceConfig cfg = repo.getDataSourceConfig();
        String apiBase = resolveApiBase(cfg.getBaseUrl());
        String token = clientFactory.getDecryptedToken(cfg);

        if (jobState != null) {
            jobState.phaseTotal = fetchTotalPrCount(apiBase, token, repo.getName());
        }

        // One query loads all existing entities; map lookup replaces per-PR SELECTs.
        Map<Integer, GitHubPullRequestEntity> existingPrs = prRepository
                .findByRepository(repo)
                .stream()
                .collect(Collectors.toMap(GitHubPullRequestEntity::getNumber, e -> e));

        try {
            List<GitHubPullRequestEntity> allSaved = new ArrayList<>();
            List<GitHubPullRequestEntity> batch = new ArrayList<>(BATCH_SIZE);
            int page = 1;
            boolean done = false;

            while (!done) {
                String url = apiBase + "/repos/" + repo.getName()
                        + "/pulls?state=all&per_page=100&page=" + page;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(
                        request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new GitHubException("GitHub API returned " + response.statusCode()
                            + " fetching PRs for " + repo.getName());
                }

                JsonNode prs = objectMapper.readTree(response.body());
                if (!prs.isArray() || prs.isEmpty()) break;

                for (JsonNode node : prs) {
                    if (jobState != null) {
                        jobState.phaseProcessed.incrementAndGet();
                        jobState.totalProcessed.incrementAndGet();
                    }

                    int number = node.path("number").asInt();
                    Instant updatedAt = parseInstant(node.path("updated_at"));
                    GitHubPullRequestEntity existing = existingPrs.get(number);

                    // Skip PRs that haven't changed since the last sync.
                    if (existing != null
                            && existing.getUpdatedAt() != null
                            && existing.getUpdatedAt().equals(updatedAt)) {
                        continue;
                    }

                    batch.add(mapPr(existing != null ? existing : new GitHubPullRequestEntity(),
                            node, repo));

                    if (batch.size() >= BATCH_SIZE) {
                        allSaved.addAll(prRepository.saveAll(batch));
                        batch.clear();
                    }
                }

                String linkHeader = response.headers().firstValue("Link").orElse("");
                if (!linkHeader.contains("rel=\"next\"")) done = true;
                page++;
            }

            if (!batch.isEmpty()) {
                allSaved.addAll(prRepository.saveAll(batch));
            }

            cfg.setLastSuccessSync(LocalDateTime.now());
            repoRepository.save(repo);

            log.info("Ingested {} new/updated PRs for {} (all PENDING, to be enriched)",
                    allSaved.size(), repo.getName());
            return new IngestResult(allSaved, apiBase, token);

        } catch (IOException e) {
            throw new GitHubException("Failed to collect GitHub PRs for " + repo.getName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("Interrupted while collecting GitHub PRs for " + repo.getName(), e);
        }
    }

    /**
     * Carries the result of a PR ingest run: the saved entities plus the HTTP
     * coordinates needed by the enrichment service.
     */
    public record IngestResult(
            List<GitHubPullRequestEntity> savedEntities,
            String apiBase,
            String token
    ) {}

    /**
     * Maps a PR list-response JSON node onto an entity.
     * Fields not present in the list response (additions, deletions, changedFiles, commitsCount)
     * are preserved from the existing entity, or defaulted to 0 for new PRs.
     */
    private GitHubPullRequestEntity mapPr(GitHubPullRequestEntity entity,
                                          JsonNode node,
                                          GitRepositoryEntity repo) {
        entity.setRepository(repo);
        entity.setNumber(node.path("number").asInt());
        entity.setTitle(node.path("title").asText(null));

        JsonNode userNode = node.path("user");
        entity.setAuthorLogin(userNode.isMissingNode() || userNode.isNull()
                ? null : userNode.path("login").asText(null));

        entity.setState(node.path("state").asText("open"));

        Instant createdAt = parseInstant(node.path("created_at"));
        Instant mergedAt  = parseInstant(node.path("merged_at"));
        entity.setCreatedAt(createdAt);
        entity.setMerged(mergedAt != null);
        entity.setMergedAt(mergedAt);
        entity.setUpdatedAt(parseInstant(node.path("updated_at")));
        entity.setClosedAt(parseInstant(node.path("closed_at")));

        // Lead time: hours from PR creation to merge. Calculated locally from timestamps
        // already present in the list response — no extra API call required.
        if (mergedAt != null && createdAt != null) {
            entity.setLeadTimeHours(java.time.Duration.between(createdAt, mergedAt).toHours());
        } else {
            entity.setLeadTimeHours(null);
        }

        entity.setCommentsCount(node.path("comments").asInt(0));
        entity.setReviewCommentsCount(node.path("review_comments").asInt(0));

        // Size stats (additions/deletions/changedFiles/commitsCount) are absent from the
        // list endpoint. Apply enrichment state logic:
        //   - New PR: mark PENDING so the enricher fetches the detail endpoint.
        //   - Existing open PR that changed: reset to PENDING — its diff may have grown.
        //   - Existing merged PR already COMPLETE: leave alone — diff is immutable after merge.
        if (entity.getId() == null) {
            entity.setAdditions(0);
            entity.setDeletions(0);
            entity.setChangedFiles(0);
            entity.setCommitsCount(0);
            entity.setStatsStatus(StatsStatus.PENDING);
            entity.setStatsAttempts(0);
        } else if (!entity.isMerged() && entity.getStatsStatus() != StatsStatus.PENDING) {
            // Open PR that was updated — stats may have changed with new commits.
            entity.setStatsStatus(StatsStatus.PENDING);
            entity.setStatsAttempts(0);
        }
        // else: merged COMPLETE PR — keep existing stats, no re-fetch needed.

        return entity;
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

    private int fetchTotalPrCount(String apiBase, String token, String repoFullName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/repos/" + repoFullName + "/pulls?state=all&per_page=1"))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<Void> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.discarding());

            String linkHeader = response.headers().firstValue("Link").orElse("");
            Matcher m = LAST_PAGE_PATTERN.matcher(linkHeader);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            log.debug("Could not fetch total PR count for {}: {}", repoFullName, e.getMessage());
        }
        return -1;
    }

    @Transactional(readOnly = true)
    public Page<GitHubPullRequestEntity> listPullRequests(Long repoId, Pageable pageable) {
        GitRepositoryEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + repoId));
        return prRepository.findByRepositoryOrderByCreatedAtDesc(repo, pageable);
    }
}
