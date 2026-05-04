package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates two-phase GitHub commit ingestion for a single repository.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li><b>Phase A — ingest:</b> {@link GitHubCommitIngestService} walks the
 *       {@code /commits} list endpoint and saves new commits with
 *       {@code statsStatus=PENDING}. No per-commit detail calls are made.
 *       The watermark ({@code lastFetchedCommitHash}) is updated here.</li>
 *   <li><b>Phase B — immediate enrichment:</b> the newest
 *       {@value GitHubCommitStatsEnrichmentService#IMMEDIATE_ENRICH_LIMIT} commits
 *       are enriched synchronously using the detail endpoint so that current
 *       dashboards reflect fresh stats right away.</li>
 *   <li><b>Phase C — background backfill:</b> the remaining PENDING commits are
 *       handled by {@link CommitStatsEnrichmentScheduler} which runs every 2 minutes
 *       in the background.</li>
 * </ol>
 *
 * <p>This design removes the bottleneck where all per-commit detail requests had to
 * complete before the sync job could return, and keeps total request volume per run
 * proportional only to the priority window, not the full history.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubCollector {

    private final GitHubCommitIngestService ingestService;
    private final GitHubCommitStatsEnrichmentService enrichmentService;

    /**
     * Runs the full two-phase collection for one repository.
     *
     * @return total number of commits newly saved (all phases, including PENDING ones)
     */
    public int collectForRepository(Long gitRepoId, SyncJobTracker.JobState jobState) {
        // Phase A: fast ingest — saves all new commits as PENDING.
        GitHubCommitIngestService.IngestResult ingest =
                ingestService.ingestForRepository(gitRepoId, jobState);

        List<GitCommitEntity> saved = ingest.savedEntities();
        if (saved.isEmpty()) {
            log.debug("No new commits for repo id={}", gitRepoId);
            return 0;
        }

        log.info("Ingested {} new commits for repo id={}, starting immediate enrichment of top {}",
                saved.size(), gitRepoId, GitHubCommitStatsEnrichmentService.IMMEDIATE_ENRICH_LIMIT);

        // Phase B: immediate enrichment of the priority window — newest commits first.
        // Sort explicitly so the limit always picks the most recent ones regardless of
        // the order JPA returns from saveAll.
        saved.sort(Comparator.comparing(GitCommitEntity::getAuthorDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        enrichmentService.enrichImmediate(
                saved,
                ingest.apiBase(),
                ingest.token(),
                extractRepoFullName(saved));

        // Phase C (background) is handled by CommitStatsEnrichmentScheduler — no action needed here.
        int pending = (int) saved.stream()
                .filter(c -> c.getStatsStatus() == StatsStatus.PENDING)
                .count();
        if (pending > 0) {
            log.info("{} commits queued for background enrichment (repo id={})", pending, gitRepoId);
        }

        return saved.size();
    }

    private static String extractRepoFullName(List<GitCommitEntity> commits) {
        if (commits.isEmpty()) return "unknown";
        GitCommitEntity first = commits.get(0);
        String fullName = first.getRepository().getRepoFullName();
        return fullName != null ? fullName : first.getRepository().getName();
    }
}
