package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.metrics.MetricCoverageRepository;
import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drops everything computed for one user: their snapshots and their coverage ledger.
 *
 * <p>A separate bean purely so the two deletes share one transaction: a self-invoked
 * {@code @Transactional} method on the non-transactional {@code MetricBackfillService} would
 * bypass the proxy and silently run without one. Coverage surviving a failed snapshot delete
 * would claim those days were computed and the backfill would never revisit them.</p>
 */
@Service
@RequiredArgsConstructor
public class UserMetricsPurger {

    private final MetricSnapshotRepository snapshotRepository;
    private final MetricCoverageRepository coverageRepository;

    @Transactional
    public void purge(Long userId) {
        snapshotRepository.deleteByUserId(userId);
        coverageRepository.deleteByUserId(userId);
    }
}
