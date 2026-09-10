package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.juliashtal.devanalytics.helper.ParsingHelper.resolveApiBase;

/**
 * {@link GitHubIdentityBackfill} over the GitHub REST API.
 *
 * <p>Deliberately narrow: each step reads the same list endpoints the collectors already use and
 * writes only the identity columns. Stats, timestamps and enrichment state are never touched, so
 * a backfill cannot undo enrichment work or reset a PR to PENDING.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubIdentityBackfillService implements GitHubIdentityBackfill {

    private static final int PAGE_SIZE = 100;

    /** Same pacing the collectors use (~1.4 req/s), to stay clear of secondary rate limits. */
    private static final long PAGE_PAUSE_MS = 700;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final GitCommitEntityRepository commitRepository;
    private final GitHubPullRequestRepository prRepository;
    private final GitHubPrStatsEnrichmentService prEnrichmentService;
    private final GitHubIssuesCollector issuesCollector;
    private final GitHubClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void backfillCommits(GitRepositoryEntity repo) {
        Context ctx = contextFor(repo);
        int updated = 0;
        int page = 1;

        while (true) {
            JsonNode nodes = getArray(ctx, "/repos/" + repo.getName()
                    + "/commits?per_page=" + PAGE_SIZE + "&page=" + page, "commit identity backfill");
            if (nodes.isEmpty()) break;

            List<GitCommitEntity> dirty = new ArrayList<>();
            for (JsonNode node : nodes) {
                String hash = node.path("sha").asText(null);
                if (hash == null) continue;

                // Null `author` means GitHub matched the email to no account -- a real answer,
                // and one the declared-address path still covers.
                JsonNode author = node.path("author");
                if (!author.isObject()) continue;

                Long githubId = author.path("id").isNumber() ? author.path("id").asLong() : null;
                if (githubId == null) continue;

                Optional<GitCommitEntity> existing = commitRepository.findByHash(hash);
                if (existing.isEmpty()) continue;

                GitCommitEntity commit = existing.get();
                // Skip rows already carrying the ID so re-runs do no writes at all.
                if (githubId.equals(commit.getAuthorGithubId())) continue;

                commit.setAuthorGithubId(githubId);
                commit.setAuthorGithubLogin(author.path("login").asText(null));
                dirty.add(commit);
            }

            if (!dirty.isEmpty()) {
                commitRepository.saveAll(dirty);
                updated += dirty.size();
            }
            if (nodes.size() < PAGE_SIZE) break;
            page++;
            pause();
        }

        log.info("Commit identity backfill for {}: {} commits updated", repo.getName(), updated);
    }

    @Override
    @Transactional
    public void backfillPullRequests(GitRepositoryEntity repo) {
        Context ctx = contextFor(repo);
        int updated = 0;
        int page = 1;

        while (true) {
            JsonNode nodes = getArray(ctx, "/repos/" + repo.getName()
                    + "/pulls?state=all&per_page=" + PAGE_SIZE + "&page=" + page, "PR identity backfill");
            if (nodes.isEmpty()) break;

            List<GitHubPullRequestEntity> dirty = new ArrayList<>();
            for (JsonNode node : nodes) {
                JsonNode user = node.path("user");
                if (!user.isObject() || !user.path("id").isNumber()) continue;

                long githubId = user.path("id").asLong();
                int number = node.path("number").asInt(-1);
                if (number < 0) continue;

                Optional<GitHubPullRequestEntity> existing =
                        prRepository.findByRepositoryAndNumber(repo, number);
                if (existing.isEmpty()) continue;

                GitHubPullRequestEntity pr = existing.get();
                if (Long.valueOf(githubId).equals(pr.getAuthorGithubId())) continue;

                pr.setAuthorGithubId(githubId);
                dirty.add(pr);
            }

            if (!dirty.isEmpty()) {
                prRepository.saveAll(dirty);
                updated += dirty.size();
            }
            if (nodes.size() < PAGE_SIZE) break;
            page++;
            pause();
        }

        log.info("PR identity backfill for {}: {} PRs updated", repo.getName(), updated);
    }

    @Override
    @Transactional
    public void refreshReviews(GitRepositoryEntity repo) {
        Context ctx = contextFor(repo);
        List<GitHubPullRequestEntity> prs = prRepository.findByRepository(repo);
        int refreshed = 0;

        for (GitHubPullRequestEntity pr : prs) {
            try {
                // Replaces review rows wholesale so they carry reviewer IDs; enrichment never
                // would, since a COMPLETE merged PR is deliberately not re-fetched.
                prEnrichmentService.refreshReviews(pr, ctx.apiBase(), ctx.token(), repo.getName());
                refreshed++;
                pause();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GitHubException("Interrupted refreshing reviews for " + repo.getName(), e);
            } catch (Exception e) {
                // One unreachable PR must not abandon the rest; the null marker retries the repo.
                throw new GitHubException("Failed refreshing reviews for PR #" + pr.getNumber()
                        + " in " + repo.getName(), e);
            }
        }

        log.info("Review identity refresh for {}: {} PRs refreshed", repo.getName(), refreshed);
    }

    @Override
    public void backfillIssues(GitRepositoryEntity repo) {
        if (!repo.isCollectIssues()) {
            log.debug("Skipping issue backfill for {}: issue collection is off", repo.getName());
            return;
        }
        // Issue collection upserts every issue each run, so running it is the backfill.
        issuesCollector.collectIssuesForRepo(repo.getDataSourceConfig(), repo);
    }

    // -------------------------------------------------------------------------
    // HTTP
    // -------------------------------------------------------------------------

    private record Context(String apiBase, String token) {}

    private Context contextFor(GitRepositoryEntity repo) {
        DataSourceConfig cfg = repo.getDataSourceConfig();
        return new Context(resolveApiBase(cfg.getBaseUrl()), clientFactory.getDecryptedToken(cfg));
    }

    private JsonNode getArray(Context ctx, String path, String what) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ctx.apiBase() + path))
                .header("Authorization", "Bearer " + ctx.token())
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new GitHubException("GitHub returned " + response.statusCode() + " during " + what);
            }
            JsonNode nodes = objectMapper.readTree(response.body());
            // A non-array body is an error payload with a 200; treating it as "no more pages"
            // would silently truncate the backfill.
            if (!nodes.isArray()) {
                throw new GitHubException("Expected a JSON array during " + what);
            }
            return nodes;
        } catch (GitHubException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("Interrupted during " + what, e);
        } catch (Exception e) {
            throw new GitHubException("Could not reach GitHub during " + what, e);
        }
    }

    private void pause() {
        try {
            Thread.sleep(PAGE_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("Interrupted while pacing GitHub requests", e);
        }
    }
}
