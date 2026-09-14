package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.scheduler.MetricsSummaryScheduler;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.config.SystemClock;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.notification.NotificationDispatchService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins two properties of the weekly job: it computes the window it then summarises, and that
 * window is the seven UTC days ending yesterday rather than a server-zone reading of them.
 */
@ExtendWith(MockitoExtension.class)
class MetricsSummarySchedulerTest {

    @Mock UserRepository userRepository;
    @Mock MetricsService metricsService;
    @Mock MetricsAiService metricsAiService;
    @Mock NotificationDispatchService notificationDispatch;

    /** A Monday 08:00 UTC firing, the cron this job is registered under. */
    private static final Instant FIXED = Instant.parse("2026-03-16T08:00:00Z");
    private static final LocalDate TO   = LocalDate.of(2026, 3, 15);
    private static final LocalDate FROM = LocalDate.of(2026, 3, 9);

    MetricsSummaryScheduler scheduler;

    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @BeforeEach
    void setUp() {
        scheduler = new MetricsSummaryScheduler(userRepository, metricsService, metricsAiService,
                notificationDispatch, new SystemClock(Clock.fixed(FIXED, ZoneOffset.UTC)));
    }

    @Test
    void generateWeeklySummaries_computesTheSummarisedWindowBeforeGeneratingIt() {
        User user = user(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(metricsAiService.generateSummary(any(), any(), any(), any()))
                .thenReturn(MetricsSummaryDto.builder().headline("h").build());

        scheduler.generateWeeklySummaries();

        InOrder order = inOrder(metricsService, metricsAiService);
        order.verify(metricsService).calculateDailyMetrics(eq(1L), any(), any());
        order.verify(metricsAiService).generateSummary(eq(user), any(), any(), eq(null));
    }

    @Test
    void generateWeeklySummaries_computesTheSameWindowItThenSummarises() {
        User user = user(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(metricsAiService.generateSummary(any(), any(), any(), any()))
                .thenReturn(MetricsSummaryDto.builder().headline("h").build());

        scheduler.generateWeeklySummaries();

        ArgumentCaptor<LocalDate> calcFrom = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> calcTo   = ArgumentCaptor.forClass(LocalDate.class);
        verify(metricsService).calculateDailyMetrics(eq(1L), calcFrom.capture(), calcTo.capture());

        ArgumentCaptor<LocalDate> aiFrom = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> aiTo   = ArgumentCaptor.forClass(LocalDate.class);
        verify(metricsAiService).generateSummary(eq(user), aiFrom.capture(), aiTo.capture(), eq(null));

        assertThat(calcFrom.getValue()).isEqualTo(aiFrom.getValue());
        assertThat(calcTo.getValue()).isEqualTo(aiTo.getValue());
        assertThat(calcFrom.getValue()).isEqualTo(FROM);
        assertThat(calcTo.getValue()).isEqualTo(TO);
    }

    @Test
    void generateWeeklySummaries_oneUsersCalculationFailing_doesNotStopTheRest() {
        User failing = user(1L);
        User healthy = user(2L);
        when(userRepository.findAll()).thenReturn(List.of(failing, healthy));
        org.mockito.Mockito.doThrow(new IllegalStateException("no data source"))
                .when(metricsService).calculateDailyMetrics(eq(1L), any(), any());
        when(metricsAiService.generateSummary(eq(healthy), any(), any(), any()))
                .thenReturn(MetricsSummaryDto.builder().headline("h").build());

        scheduler.generateWeeklySummaries();

        verify(metricsAiService, times(1)).generateSummary(eq(healthy), any(), any(), eq(null));
        verify(metricsAiService, org.mockito.Mockito.never()).generateSummary(eq(failing), any(), any(), any());
    }

    /**
     * The job fires at 08:00 UTC, an hour at which Auckland has already entered the next day and
     * Los Angeles is still in the previous one; the summarised week must not move with either.
     */
    @Test
    void generateWeeklySummaries_serverZoneEastOrWestOfUtc_summarisesTheSameWeek() {
        User user = user(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(metricsAiService.generateSummary(any(), any(), any(), any()))
                .thenReturn(MetricsSummaryDto.builder().headline("h").build());

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
        scheduler.generateWeeklySummaries();

        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        scheduler.generateWeeklySummaries();

        verify(metricsAiService, times(2)).generateSummary(user, FROM, TO, null);
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }
}
