package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.model.AggregatedMetricsContext;
import com.juliashtal.devanalytics.ai.model.GoalEntity;
import com.juliashtal.devanalytics.ai.repository.GoalRepository;
import com.juliashtal.devanalytics.ai.service.AiContextBuilderService;
import com.juliashtal.devanalytics.config.SystemClock;
import com.juliashtal.devanalytics.metrics.service.AggregateWindowResolver;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what reaches the model as active goals: the cut-off date is the system clock's today, so
 * goal filtering does not move with the zone the server happens to run in.
 */
@ExtendWith(MockitoExtension.class)
class AiContextBuilderServiceTest {

    /** Late enough in UTC that Auckland has rolled into 2026-03-15 and Los Angeles has not. */
    private static final Instant FIXED = Instant.parse("2026-03-15T00:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Mock MetricSnapshotService metricSnapshotService;
    @Mock GoalRepository goalRepository;

    AiContextBuilderService contextBuilder;

    private User user;

    private final TimeZone originalZone = TimeZone.getDefault();

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @BeforeEach
    void setUp() {
        // The resolver is pure computation, so the real one is used rather than a mock.
        contextBuilder = new AiContextBuilderService(
                metricSnapshotService, new AggregateWindowResolver(), goalRepository,
                new SystemClock(Clock.fixed(FIXED, ZoneOffset.UTC)));

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setTimezone("UTC");

        // Empty by default. Lenient: the goal-progress lookup only fires for an active goal.
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void buildPersonalContext_withActiveGoal_includesGoalInContext() {
        GoalEntity goal = new GoalEntity();
        goal.setId(1L);
        goal.setUser(user);
        goal.setMetricType("DAILY_COMMITS_COUNT");
        goal.setTargetValue(10.0);
        goal.setTargetDate(TODAY.plusDays(7));
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
    void buildPersonalContext_anyServerZone_filtersGoalsFromTheSameCutOffDate() {
        when(goalRepository.findByUser_IdAndTargetDateGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
        contextBuilder.buildPersonalContext(user, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 14), null);

        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        contextBuilder.buildPersonalContext(user, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 14), null);

        verify(goalRepository, org.mockito.Mockito.times(2))
                .findByUser_IdAndTargetDateGreaterThanEqual(user.getId(), TODAY);
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
