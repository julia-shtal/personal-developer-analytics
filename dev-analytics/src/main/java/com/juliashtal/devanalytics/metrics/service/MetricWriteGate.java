package com.juliashtal.devanalytics.metrics.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serialises the scheduled jobs that write {@code metric_snapshots}; request threads, the
 * {@code collect-} pool and the attribution listener still write outside it.
 *
 * <p>{@code uix_metric_snapshots_identity} (V72) now closes the identity race at the database
 * for every caller, so this gate is a secondary throttle on the scheduled writers specifically —
 * reducing wasted recomputation when two scheduled runs overlap — rather than what prevents a
 * duplicate row.</p>
 */
@Component
public class MetricWriteGate {

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Runs {@code job} only while no other writer holds the gate.
     *
     * <p>Skips rather than queues: each caller recomputes from persisted state, so the next run
     * covers whatever this one declined.</p>
     *
     * @return false when the job was skipped because another writer was running
     */
    public boolean runExclusively(Runnable job) {
        if (!lock.tryLock()) {
            return false;
        }
        try {
            job.run();
            return true;
        } finally {
            lock.unlock();
        }
    }
}
