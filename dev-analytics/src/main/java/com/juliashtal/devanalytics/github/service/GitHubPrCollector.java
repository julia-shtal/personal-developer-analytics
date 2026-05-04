package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates two-phase GitHub PR collection for a single repository,
 * mirroring the commit collection design in {@link GitHubCollector}.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li><b>Phase A — ingest:</b> {@link GitHubPullRequestCollector} pages through the
 *       GitHub PR list endpoint and saves new or changed PRs with
 *       {@code statsStatus=PENDING}. No review fetches or per-PR detail calls are made.
 *       Returns immediately after all pages are saved.</li>
 *   <li><b>Phase B — immediate enrichment:</b> the newest
 *       {@value GitHubPrStatsEnrichmentService#IMMEDIATE_ENRICH_LIMIT} PENDING PRs are
 *       enriched synchronously (reviews + size stats) so dashboards reflect fresh data
 *       right away.</li>
 *   <li><b>Phase C — background backfill:</b> remaining PENDING PRs are handled by
 *       {@link CommitStatsEnrichmentScheduler} which runs every 2 minutes.</li>
 * </ol>
 *
 * <p>This removes the bottleneck where review fetches and detail calls for every PR
 * had to complete before the sync job could return.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubPrCollector {

    private final GitHubPullRequestCollector ingestService;
    private final GitHubPrStatsEnrichmentService enrichmentService;

    /**
     * Runs the full two-phase collection for one repository.
     *
     * @return total number of PRs newly saved or updated (includes those still PENDING)
     */
    public int collectForRepository(Long gitRepoId, SyncJobTracker.JobState jobState) {
        // Phase A: fast ingest — saves all new/changed PRs as PENDING.
        GitHubPullRequestCollector.IngestResult ingest =
                ingestService.collectPullRequests(gitRepoId, jobState);

        List<GitHubPullRequestEntity> saved = ingest.savedEntities();
        if (saved.isEmpty()) {
            log.debug("No new or updated PRs for repo id={}", gitRepoId);
            return 0;
        }

        log.info("Ingested {} new/updated PRs for repo id={}, starting immediate enrichment of top {}",
                saved.size(), gitRepoId, GitHubPrStatsEnrichmentService.IMMEDIATE_ENRICH_LIMIT);

        // Phase B: immediate enrichment of the priority window — newest PRs first.
        // Sort explicitly so the limit always picks the most recently created ones
        // regardless of the order JPA returns from saveAll.
        saved.sort(Comparator.comparing(GitHubPullRequestEntity::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        String repoFullName = extractRepoFullName(saved);
        enrichmentService.enrichImmediate(saved, ingest.apiBase(), ingest.token(), repoFullName);

        // Phase C (background) is handled by CommitStatsEnrichmentScheduler.
        long pending = saved.stream()
                .filter(p -> p.getStatsStatus() == StatsStatus.PENDING)
                .count();
        if (pending > 0) {
            log.info("{} PRs queued for background enrichment (repo id={})", pending, gitRepoId);
        }

        return saved.size();
    }

    private static String extractRepoFullName(List<GitHubPullRequestEntity> prs) {
        if (prs.isEmpty()) return "unknown";
        GitHubPullRequestEntity first = prs.get(0);
        String fullName = first.getRepository().getRepoFullName();
        return fullName != null ? fullName : first.getRepository().getName();
    }
}
