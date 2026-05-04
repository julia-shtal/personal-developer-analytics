package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.juliashtal.devanalytics.helper.ParsingHelper.resolveApiBase;

/**
 * Background scheduler for both commit and PR stats enrichment (Phase C).
 *
 * <p>Runs every 2 minutes. Within each run, commit batches and PR batches are
 * processed <em>sequentially</em> — not in parallel — so they share the same
 * rate-limit budget and never race each other toward GitHub's secondary limits.</p>
 *
 * <p>Order per run:
 * <ol>
 *   <li>For every repo with PENDING commits: enrich one batch of 50 commits.</li>
 *   <li>For every repo with PENDING PRs: enrich one batch of 50 PRs.</li>
 * </ol>
 * Each individual enrichment call already applies its own ~1.4 req/sec rate limiter,
 * so the total throughput is bounded regardless of how many repos need backfill.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommitStatsEnrichmentScheduler {

    private final GitCommitEntityRepository commitRepository;
    private final GitHubPullRequestRepository prRepository;
    private final GitRepositoryEntityRepository repoRepository;
    private final GitHubCommitStatsEnrichmentService commitEnrichmentService;
    private final GitHubPrStatsEnrichmentService prEnrichmentService;
    private final GitHubClientFactory clientFactory;

    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void enrichPending() {
        Set<Long> repoIds = collectRepoIds();
        if (repoIds.isEmpty()) return;

        log.info("Background enrichment: {} repos have PENDING commits or PRs", repoIds.size());

        for (Long repoId : repoIds) {
            GitRepositoryEntity repo = repoRepository.findByIdWithDataSourceConfig(repoId).orElse(null);
            if (repo == null) continue;

            try {
                String apiBase = resolveApiBase(repo.getDataSourceConfig().getBaseUrl());
                String token = clientFactory.getDecryptedToken(repo.getDataSourceConfig());
                String repoFullName = repo.getRepoFullName() != null
                        ? repo.getRepoFullName()
                        : repo.getName();

                // Commits first, then PRs — sequential to stay within rate limits.
                int commits = commitEnrichmentService.processPendingBatchForRepo(
                        apiBase, token, repoFullName, repoId);
                int prs = prEnrichmentService.processPendingBatchForRepo(
                        apiBase, token, repoFullName, repoId);

                if (commits > 0 || prs > 0) {
                    log.info("Background enrichment for {}: {} commits, {} PRs", repoFullName, commits, prs);
                }
            } catch (Exception e) {
                log.warn("Background enrichment failed for repo {}: {}", repoId, e.getMessage());
            }
        }
    }

    /** Collects all repo IDs that have either PENDING commits or PENDING PRs. */
    private Set<Long> collectRepoIds() {
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(commitRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING));
        ids.addAll(prRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING));
        return ids;
    }
}
