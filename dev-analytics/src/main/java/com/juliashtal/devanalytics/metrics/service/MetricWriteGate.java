package com.juliashtal.devanalytics.metrics.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serialises the scheduled jobs that write {@code metric_snapshots}; request threads, the
 * {@code collect-} pool and the attribution listener still write outside it.
 *
 * <p>The table carries no unique key, so the guard in {@code MetricSnapshotWriter} is a
 * read-then-write: two writers computing the same window both miss {@code findExisting} and both
 * insert. A process-local lock narrows that to the scheduled writers; a unique index over the
 * {@code findExisting} columns is what would close it for every caller.</p>
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
