package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.scheduler.MetricsSummaryScheduler;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.notification.NotificationDispatchService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The weekly job summarised a window it never computed. Combined with the exact-period read,
 * that is what dropped the five period-stored metrics from every scheduled summary.
 */
@ExtendWith(MockitoExtension.class)
class MetricsSummarySchedulerTest {

    @Mock UserRepository userRepository;
    @Mock MetricsService metricsService;
    @Mock MetricsAiService metricsAiService;
    @Mock NotificationDispatchService notificationDispatch;

    @InjectMocks MetricsSummaryScheduler scheduler;

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
        assertThat(calcFrom.getValue()).isEqualTo(calcTo.getValue().minusDays(6));
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

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }
}
