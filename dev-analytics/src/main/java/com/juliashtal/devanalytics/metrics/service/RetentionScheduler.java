package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.ai.repository.MetricSummaryRepository;
import com.juliashtal.devanalytics.metrics.repository.MetricCoverageRepository;
import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Nightly job that deletes {@code metric_snapshots}, {@code metric_coverage}, and
 * {@code metric_summaries} rows whose period ends before the retention horizon.
 *
 * <p>Never touches raw ingested history (commits, pull requests, issues) — this class has no
 * dependency capable of reaching those repositories at all, which is the guarantee, not a runtime
 * check. Runs after {@link MetricsScheduler} (01:00 UTC) and {@code TokenCleanupScheduler}
 * (02:00 UTC) so it never races the nightly metric calculation, and is itself wrapped in
 * {@link MetricWriteGate} because it deletes {@code metric_snapshots}. The three deletes commit
 * independently rather than as one transaction — each repository method carries its own, by
 * design — which is safe because retention is idempotent: a table a failed run leaves un-pruned
 * is simply caught by the next one.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetentionScheduler {

    private final RetentionPolicy retentionPolicy;
    private final MetricWriteGate writeGate;
    private final MetricSnapshotRepository snapshotRepository;
    private final MetricCoverageRepository coverageRepository;
    private final MetricSummaryRepository summaryRepository;

    @Scheduled(cron = "0 0 3 * * ?", zone = "UTC")
    public void run() {
        if (!retentionPolicy.isEnabled()) {
            log.info("Retention scheduler skipped: retention disabled");
            return;
        }
        boolean ran = writeGate.runExclusively(this::deleteExpired);
        if (!ran) {
            log.info("Retention scheduler skipped: another metric writer is running");
        }
    }

    private void deleteExpired() {
        LocalDate horizon = retentionPolicy.horizon();
        log.info("Retention scheduler started: horizon={}", horizon);

        int snapshots = snapshotRepository.deleteExpired(horizon);
        int coverage = coverageRepository.deleteExpired(horizon);
        int summaries = summaryRepository.deleteExpired(horizon);

        log.info("Retention scheduler finished: deletedSnapshots={}, deletedCoverage={}, deletedSummaries={}",
                snapshots, coverage, summaries);
    }
}
