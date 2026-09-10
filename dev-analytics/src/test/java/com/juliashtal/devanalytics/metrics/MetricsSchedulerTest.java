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
 * The nightly job is incremental only: it computes yesterday for every user and attempts no gap
 * recovery, which belongs to {@code MetricBackfillScheduler}.
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
        // lenient(): only user 2 is stubbed, and PotentialStubbingProblem for the others would be
        // swallowed by the scheduler's catch-all and logged as if it were a real failure.
        lenient().doThrow(new IllegalStateException("boom"))
                .when(metricsService).calculateDailyMetrics(2L, yesterday, yesterday);

        new MetricsScheduler(metricsService, userRepository).calculateYesterday();

        verify(metricsService).calculateDailyMetrics(1L, yesterday, yesterday);
        verify(metricsService).calculateDailyMetrics(3L, yesterday, yesterday);
    }

    /**
     * Captures the actual {@code from} and {@code to} and asserts both equal yesterday, so any
     * regression that widens the range fails here rather than passing unconditionally.
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
