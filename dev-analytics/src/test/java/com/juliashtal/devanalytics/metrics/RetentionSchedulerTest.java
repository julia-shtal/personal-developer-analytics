package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.ai.repository.MetricSummaryRepository;
import com.juliashtal.devanalytics.config.SystemClock;
import com.juliashtal.devanalytics.metrics.repository.MetricCoverageRepository;
import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.service.MetricWriteGate;
import com.juliashtal.devanalytics.metrics.service.RetentionPolicy;
import com.juliashtal.devanalytics.metrics.service.RetentionScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-02T03:00:00Z");
    private static final LocalDate HORIZON = LocalDate.of(2026, 10, 2).minusMonths(24);

    @ExtendWith(MockitoExtension.class)
    static abstract class WithMocks {
        @Mock MetricSnapshotRepository snapshotRepository;
        @Mock MetricCoverageRepository coverageRepository;
        @Mock MetricSummaryRepository summaryRepository;
        MetricWriteGate writeGate = new MetricWriteGate();

        RetentionScheduler scheduler(int months) {
            RetentionPolicy policy = new RetentionPolicy(months,
                    new SystemClock(Clock.fixed(NOW, ZoneOffset.UTC)));
            return new RetentionScheduler(policy, writeGate,
                    snapshotRepository, coverageRepository, summaryRepository);
        }
    }

    @org.junit.jupiter.api.Nested
    class Enabled extends WithMocks {
        @Test
        void run_enabled_deletesExpiredRowsFromAllThreeTables() {
            scheduler(24).run();

            verify(snapshotRepository).deleteExpired(eq(HORIZON));
            verify(coverageRepository).deleteExpired(eq(HORIZON));
            verify(summaryRepository).deleteExpired(eq(HORIZON));
        }
    }

    @org.junit.jupiter.api.Nested
    class Disabled extends WithMocks {
        @Test
        void run_disabled_deletesNothing() {
            scheduler(0).run();

            verifyNoInteractions(snapshotRepository, coverageRepository, summaryRepository);
        }
    }

    @org.junit.jupiter.api.Nested
    class GateHeld extends WithMocks {
        @Test
        void run_anotherWriterHoldsTheGate_deletesNothing() {
            MetricWriteGate busyGate = mock(MetricWriteGate.class);
            when(busyGate.runExclusively(any())).thenReturn(false);
            RetentionPolicy policy = new RetentionPolicy(24,
                    new SystemClock(Clock.fixed(NOW, ZoneOffset.UTC)));

            new RetentionScheduler(policy, busyGate,
                    snapshotRepository, coverageRepository, summaryRepository).run();

            verifyNoInteractions(snapshotRepository, coverageRepository, summaryRepository);
        }
    }
}
