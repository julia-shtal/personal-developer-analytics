package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.service.MetricsScheduler;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsBackfillSchedulerTest {

    @Mock MetricsService metricsService;
    @Mock UserRepository userRepository;
    @Mock MetricSnapshotRepository snapshotRepository;

    MetricsScheduler scheduler;

    User user;

    @BeforeEach
    void setUp() {
        scheduler = new MetricsScheduler(metricsService, userRepository, snapshotRepository);
        user = new User();
        user.setId(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));
    }

    @Test
    void calculateYesterday_noGap_skipsCalculation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(snapshotRepository.findMaxPersonalDate(1L)).thenReturn(Optional.of(yesterday));

        scheduler.calculateYesterday();

        verify(metricsService, never()).calculateDailyMetrics(anyLong(), any(), any());
    }

    @Test
    void calculateYesterday_twoDayGap_fillsBothDays() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate twoDaysAgo = yesterday.minusDays(2);
        when(snapshotRepository.findMaxPersonalDate(1L)).thenReturn(Optional.of(twoDaysAgo));

        scheduler.calculateYesterday();

        ArgumentCaptor<LocalDate> fromCap = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(metricsService).calculateDailyMetrics(eq(1L), fromCap.capture(), toCap.capture());
        assertThat(fromCap.getValue()).isEqualTo(twoDaysAgo.plusDays(1));
        assertThat(toCap.getValue()).isEqualTo(yesterday);
    }

    @Test
    void calculateYesterday_gapExceedsCap_cappedToMaxBackfillDays() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // Last snapshot 40 days ago → gap of 40, should be capped at MAX_BACKFILL_DAYS
        LocalDate veryOld = yesterday.minusDays(40);
        when(snapshotRepository.findMaxPersonalDate(1L)).thenReturn(Optional.of(veryOld));

        scheduler.calculateYesterday();

        ArgumentCaptor<LocalDate> fromCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(metricsService).calculateDailyMetrics(eq(1L), fromCap.capture(), eq(yesterday));
        LocalDate cappedFrom = yesterday.minusDays(MetricsScheduler.MAX_BACKFILL_DAYS - 1);
        assertThat(fromCap.getValue()).isEqualTo(cappedFrom);
    }

    @Test
    void calculateYesterday_noSnapshotsEver_calculatesYesterdayOnly() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(snapshotRepository.findMaxPersonalDate(1L)).thenReturn(Optional.empty());

        scheduler.calculateYesterday();

        verify(metricsService).calculateDailyMetrics(1L, yesterday, yesterday);
    }
}
