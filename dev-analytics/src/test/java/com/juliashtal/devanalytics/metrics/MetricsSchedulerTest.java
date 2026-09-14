package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.config.SystemClock;
import com.juliashtal.devanalytics.metrics.service.MetricsScheduler;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The nightly job is incremental only: it computes yesterday for every user and attempts no gap
 * recovery, which belongs to {@code MetricBackfillScheduler}.
 *
 * <p>"Yesterday" is asserted against a fixed clock rather than recomputed from {@code now()}, so a
 * range that silently follows the server zone fails here.</p>
 */
@ExtendWith(MockitoExtension.class)
class MetricsSchedulerTest {

    /** Late enough in UTC that the surrounding zones disagree about the calendar day. */
    private static final Instant FIXED = Instant.parse("2026-03-15T00:30:00Z");
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 3, 14);

    @Mock MetricsService metricsService;
    @Mock UserRepository userRepository;

    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    private MetricsScheduler scheduler() {
        return new MetricsScheduler(metricsService, userRepository,
                new SystemClock(Clock.fixed(FIXED, ZoneOffset.UTC)));
    }

    private User userWithId(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    void calculateYesterday_everyUser_computesYesterdayOnly() {
        when(userRepository.findAll()).thenReturn(List.of(userWithId(1L), userWithId(2L)));

        scheduler().calculateYesterday();

        verify(metricsService).calculateDailyMetrics(1L, YESTERDAY, YESTERDAY);
        verify(metricsService).calculateDailyMetrics(2L, YESTERDAY, YESTERDAY);
        verifyNoMoreInteractions(metricsService);
    }

    @Test
    void calculateYesterday_userWithNoPriorSnapshots_isNotTreatedSpecially() {
        when(userRepository.findAll()).thenReturn(List.of(userWithId(1L)));

        scheduler().calculateYesterday();

        // No snapshot repository is consulted at all — the null high-water mark branch is gone.
        verify(metricsService).calculateDailyMetrics(1L, YESTERDAY, YESTERDAY);
    }

    @Test
    void calculateYesterday_oneUserFails_othersStillProcessed() {
        when(userRepository.findAll())
                .thenReturn(List.of(userWithId(1L), userWithId(2L), userWithId(3L)));
        // lenient(): only user 2 is stubbed, and PotentialStubbingProblem for the others would be
        // swallowed by the scheduler's catch-all and logged as if it were a real failure.
        lenient().doThrow(new IllegalStateException("boom"))
                .when(metricsService).calculateDailyMetrics(2L, YESTERDAY, YESTERDAY);

        scheduler().calculateYesterday();

        verify(metricsService).calculateDailyMetrics(1L, YESTERDAY, YESTERDAY);
        verify(metricsService).calculateDailyMetrics(3L, YESTERDAY, YESTERDAY);
    }

    /**
     * Captures the actual {@code from} and {@code to} and asserts both equal yesterday, so any
     * regression that widens the range fails here rather than passing unconditionally.
     */
    @Test
    void calculateYesterday_computesExactlyYesterdayToYesterday_neverAWiderRange() {
        when(userRepository.findAll()).thenReturn(List.of(userWithId(1L)));

        scheduler().calculateYesterday();

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(metricsService).calculateDailyMetrics(eq(1L), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(YESTERDAY);
        assertThat(toCaptor.getValue()).isEqualTo(YESTERDAY);
    }

    /**
     * At the fixed instant the two zones sit on opposite sides of the date line, so a server-zone
     * reading would produce 2026-03-14 in Auckland and 2026-03-13 in Los Angeles.
     */
    @Test
    void calculateYesterday_serverZoneEastOrWestOfUtc_computesTheSameDay() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
        when(userRepository.findAll()).thenReturn(List.of(userWithId(1L)));
        scheduler().calculateYesterday();

        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        scheduler().calculateYesterday();

        verify(metricsService, times(2)).calculateDailyMetrics(1L, YESTERDAY, YESTERDAY);
        verifyNoMoreInteractions(metricsService);
    }
}
