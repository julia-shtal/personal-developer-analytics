package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.service.MetricsScheduler;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The nightly job is now incremental only: it computes yesterday for every user and does not
 * attempt gap recovery. Recovering skipped days moved to {@code MetricBackfillScheduler},
 * which reads the {@code metric_coverage} ledger rather than a MAX(date) watermark, so its
 * per-run cap resumes instead of silently truncating.
 */
@ExtendWith(MockitoExtension.class)
class MetricsSchedulerTest {

    @Mock MetricsService metricsService;
    @Mock UserRepository userRepository;

    private User userWithId(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    void calculateYesterday_everyUser_computesYesterdayOnly() {
        when(userRepository.findAll()).thenReturn(List.of(userWithId(1L), userWithId(2L)));
        LocalDate yesterday = LocalDate.now().minusDays(1);

        new MetricsScheduler(metricsService, userRepository).calculateYesterday();

        verify(metricsService).calculateDailyMetrics(1L, yesterday, yesterday);
        verify(metricsService).calculateDailyMetrics(2L, yesterday, yesterday);
        verifyNoMoreInteractions(metricsService);
    }

    @Test
    void calculateYesterday_userWithNoPriorSnapshots_isNotTreatedSpecially() {
        when(userRepository.findAll()).thenReturn(List.of(userWithId(1L)));
        LocalDate yesterday = LocalDate.now().minusDays(1);

        new MetricsScheduler(metricsService, userRepository).calculateYesterday();

        // No snapshot repository is consulted at all — the null high-water mark branch is gone.
        verify(metricsService).calculateDailyMetrics(1L, yesterday, yesterday);
    }

    @Test
    void calculateYesterday_oneUserFails_othersStillProcessed() {
        when(userRepository.findAll())
                .thenReturn(List.of(userWithId(1L), userWithId(2L), userWithId(3L)));
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // lenient(): only user 2 is stubbed, so calls for users 1 and 3 would otherwise raise
        // Mockito's PotentialStubbingProblem — which the scheduler's catch-all swallows and logs
        // exactly like a real failure. The test still passed, but the log became unreadable:
        // nobody reading it could tell a genuine scheduler failure from a too-strict mock.
        lenient().doThrow(new IllegalStateException("boom"))
                .when(metricsService).calculateDailyMetrics(2L, yesterday, yesterday);

        new MetricsScheduler(metricsService, userRepository).calculateYesterday();

        verify(metricsService).calculateDailyMetrics(1L, yesterday, yesterday);
        verify(metricsService).calculateDailyMetrics(3L, yesterday, yesterday);
    }

    /**
     * Positive-contract replacement for the plan's {@code calculateYesterday_neverNarrowsTheRange}.
     * That test asserted {@code verify(metricsService, never()).calculateDailyMetrics(anyLong(),
     * eq(LocalDate.now().minusDays(30)), any())} — after this rewrite the scheduler only ever
     * calls with {@code (yesterday, yesterday)}, so that assertion passes unconditionally and
     * cannot fail even if a 30-day-cap regression were reintroduced with a different from-date.
     *
     * <p>This version instead captures the actual {@code from} and {@code to} arguments and
     * asserts both equal yesterday. A regression that widens the range — e.g. reintroducing a
     * cap that moves {@code from} back 30 days — makes {@code fromCaptor}'s value diverge from
     * {@code yesterday} and fails this test. No {@code MetricSnapshotRepository} mock is
     * declared: the rewritten {@code MetricsScheduler} constructor is two-arg, so a stray
     * three-arg construction (the old watermark dependency reappearing) fails to compile rather
     * than needing a runtime assertion.
     */
    @Test
    void calculateYesterday_computesExactlyYesterdayToYesterday_neverAWiderRange() {
        when(userRepository.findAll()).thenReturn(List.of(userWithId(1L)));
        LocalDate yesterday = LocalDate.now().minusDays(1);

        new MetricsScheduler(metricsService, userRepository).calculateYesterday();

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(metricsService).calculateDailyMetrics(eq(1L), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(yesterday);
        assertThat(toCaptor.getValue()).isEqualTo(yesterday);
    }
}
