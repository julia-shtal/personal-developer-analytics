package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.metrics.MetricCoverageRepository;
import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drops everything computed for one user: their snapshots and their coverage ledger.
 *
 * <p>A separate bean purely so the two deletes share one transaction. {@code MetricBackfillService}
 * cannot do it itself — it is deliberately non-transactional so the recomputation that follows can
 * commit block by block, and a self-invoked {@code @Transactional} method would bypass the proxy
 * and silently run without one.
 *
 * <p>Both deletes must commit together. If coverage survived a failed snapshot delete it would
 * still claim those days were computed, and the backfill — which subtracts coverage from its
 * target range — would never revisit them, leaving the user permanently short of metrics.
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
