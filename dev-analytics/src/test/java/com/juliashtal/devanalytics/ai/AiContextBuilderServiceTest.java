package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.model.AggregatedMetricsContext;
import com.juliashtal.devanalytics.ai.model.GoalEntity;
import com.juliashtal.devanalytics.ai.repository.GoalRepository;
import com.juliashtal.devanalytics.ai.service.AiContextBuilderService;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiContextBuilderServiceTest {

    @Mock MetricSnapshotService metricSnapshotService;
    @Mock GoalRepository goalRepository;
    @InjectMocks AiContextBuilderService contextBuilder;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setTimezone("UTC");

        // Stub all metricSnapshotService calls to return empty by default
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), any(), any(), any())).thenReturn(List.of());
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void buildPersonalContext_withActiveGoal_includesGoalInContext() {
        GoalEntity goal = new GoalEntity();
        goal.setId(1L);
        goal.setUser(user);
        goal.setMetricType("DAILY_COMMITS_COUNT");
        goal.setTargetValue(10.0);
        goal.setTargetDate(LocalDate.now().plusDays(7));
        when(goalRepository.findByUser_IdAndTargetDateGreaterThanEqual(eq(user.getId()), any()))
                .thenReturn(List.of(goal));

        AggregatedMetricsContext ctx = contextBuilder.buildPersonalContext(
                user, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null);

        assertThat(ctx.getActiveGoals()).hasSize(1);
        assertThat(ctx.getActiveGoals().get(0).metricType()).isEqualTo("DAILY_COMMITS_COUNT");
        assertThat(ctx.getActiveGoals().get(0).targetValue()).isEqualTo(10.0);
        assertThat(ctx.getActiveGoals().get(0).currentValue()).isNull();
    }

    @Test
    void buildPersonalContext_noGoals_activeGoalsEmpty() {
        when(goalRepository.findByUser_IdAndTargetDateGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AggregatedMetricsContext ctx = contextBuilder.buildPersonalContext(
                user, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null);

        assertThat(ctx.getActiveGoals()).isEmpty();
    }
}
