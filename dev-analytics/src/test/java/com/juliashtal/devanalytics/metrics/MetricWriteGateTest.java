package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.service.MetricWriteGate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins that only one metric writer runs at a time, and that a declined run does not execute.
 *
 * <p>Contention is produced with a real second thread rather than a re-entrant call: the gate
 * holds a {@link java.util.concurrent.locks.ReentrantLock}, so a same-thread attempt would be
 * admitted and the test would pass without proving anything.</p>
 */
class MetricWriteGateTest {

    @Test
    void runExclusively_noContention_runsJobAndReturnsTrue() {
        MetricWriteGate gate = new MetricWriteGate();
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean result = gate.runExclusively(() -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
    }

    @Test
    void runExclusively_otherWriterHolding_skipsJobAndReturnsFalse() throws Exception {
        MetricWriteGate gate = new MetricWriteGate();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean secondJobRan = new AtomicBoolean(false);

        Thread holder = new Thread(() -> gate.runExclusively(() -> {
            holding.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        boolean result = gate.runExclusively(() -> secondJobRan.set(true));

        release.countDown();
        holder.join(5_000);

        assertThat(result).isFalse();
        assertThat(secondJobRan).isFalse();
    }

    @Test
    void runExclusively_jobThrows_releasesGateForTheNextRun() {
        MetricWriteGate gate = new MetricWriteGate();

        assertThatThrownBy(() -> gate.runExclusively(() -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        AtomicBoolean ran = new AtomicBoolean(false);
        assertThat(gate.runExclusively(() -> ran.set(true))).isTrue();
        assertThat(ran).isTrue();
    }
}
