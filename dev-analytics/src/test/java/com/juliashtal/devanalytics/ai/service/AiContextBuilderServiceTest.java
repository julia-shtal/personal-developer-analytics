package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.AggregatedMetricsContext;
import com.juliashtal.devanalytics.ai.model.TeamMetricsContext;
import com.juliashtal.devanalytics.ai.repository.GoalRepository;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiContextBuilderServiceTest {

    @Mock MetricSnapshotService metricSnapshotService;
    @Mock GoalRepository goalRepository;

    AiContextBuilderService service;

    private final User user = new User();
    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO   = LocalDate.of(2024, 1, 31);

    @BeforeEach
    void setUp() {
        service = new AiContextBuilderService(metricSnapshotService, goalRepository);

        // Base: all five query variants return empty list.
        // Tests that need data override the specific (type-scoped) variant.
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void buildPersonalContext_noSnapshots_returnsEmptyMetrics() {
        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        assertThat(ctx.getFrom()).isEqualTo(FROM);
        assertThat(ctx.getTo()).isEqualTo(TO);
        assertThat(ctx.getRepoName()).isNull();
        assertThat(ctx.getMetrics()).isEmpty();
    }

    @Test
    void buildPersonalContext_withDailySnapshots_computesTrendAndAggregate() {
        // 4 daily snapshots for a SUM metric (DAILY_COMMITS_COUNT).
        // earlyAvg = (3+5)/2 = 4.0, recentAvg = (7+9)/2 = 8.0 → trendPct = 100.0
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(
                        snapshot(FROM,              3.0),
                        snapshot(FROM.plusDays(1),  5.0),
                        snapshot(FROM.plusDays(2),  7.0),
                        snapshot(FROM.plusDays(3),  9.0)
                ));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        AggregatedMetricsContext.MetricAggregate agg =
                ctx.getMetrics().get(MetricType.DAILY_COMMITS_COUNT.name());
        assertThat(agg).isNotNull();
        assertThat(agg.getMin()).isEqualTo("3");
        assertThat(agg.getMax()).isEqualTo("9");
        assertThat(agg.getMedian()).isEqualTo("6");
        assertThat(agg.getTotal()).isEqualTo(24L);
        assertThat(agg.getTrendPct()).isEqualTo(100.0);
        assertThat(agg.isAnomaly()).isFalse();
    }

    @Test
    void buildPersonalContext_withAggregateMetric_callsDateFromToQuery() {
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.PR_LEAD_TIME_HOURS_MEDIAN), any(), any()))
                .thenReturn(List.of(snapshot(FROM, 24.0)));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        assertThat(ctx.getMetrics()).containsKey(MetricType.PR_LEAD_TIME_HOURS_MEDIAN.name());
        verify(metricSnapshotService, never())
                .getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                        any(), eq(MetricType.PR_LEAD_TIME_HOURS_MEDIAN), any(), any());
        AggregatedMetricsContext.MetricAggregate agg =
                ctx.getMetrics().get(MetricType.PR_LEAD_TIME_HOURS_MEDIAN.name());
        assertThat(agg.getMin()).isEqualTo("24");
        assertThat(agg.getMedian()).isEqualTo("24");
    }

    @Test
    void buildPersonalContext_withRepoScope_callsRepoScopedQueryAndSetsRepoName() {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(1L);
        repo.setName("my-repo");

        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), eq(repo), any(), any()))
                .thenReturn(List.of(snapshot(FROM, 7.0)));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, repo);

        assertThat(ctx.getRepoName()).isEqualTo("my-repo");
        assertThat(ctx.getMetrics()).containsKey(MetricType.DAILY_COMMITS_COUNT.name());
        verify(metricSnapshotService, never())
                .getMetricSnapshotsByUserAndMetricTypeAndDateBetween(any(), any(), any(), any());
    }

    @Test
    void buildPersonalContext_withRepoScopedAggregateMetric_callsRepoDateFromToQuery() {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        repo.setName("other-repo");

        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo(
                any(), eq(MetricType.PR_LEAD_TIME_HOURS_MEDIAN), eq(repo), any(), any()))
                .thenReturn(List.of(snapshot(FROM, 48.0)));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, repo);

        assertThat(ctx.getMetrics()).containsKey(MetricType.PR_LEAD_TIME_HOURS_MEDIAN.name());
        verify(metricSnapshotService, never())
                .getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(
                        any(), eq(MetricType.PR_LEAD_TIME_HOURS_MEDIAN), any(), any(), any());
    }

    @Test
    void buildPersonalContext_singleSnapshot_trendPctIsZero() {
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(snapshot(FROM, 5.0)));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        AggregatedMetricsContext.MetricAggregate agg =
                ctx.getMetrics().get(MetricType.DAILY_COMMITS_COUNT.name());
        assertThat(agg.getTrendPct()).isEqualTo(0.0);
    }

    @Test
    void buildPersonalContext_twoSnapshots_anomalyIsFalse() {
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(
                        snapshot(FROM,             1.0),
                        snapshot(FROM.plusDays(1), 100.0)
                ));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        assertThat(ctx.getMetrics().get(MetricType.DAILY_COMMITS_COUNT.name()).isAnomaly()).isFalse();
    }

    @Test
    void buildPersonalContext_outlierInSixValueSeries_anomalyIsTrue() {
        // mean≈17.5, stdDev≈36.9 → |100-17.5|=82.5 > 2*36.9=73.8 → anomaly=true
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(
                        snapshot(FROM,               1.0),
                        snapshot(FROM.plusDays(1),   1.0),
                        snapshot(FROM.plusDays(2),   1.0),
                        snapshot(FROM.plusDays(3),   1.0),
                        snapshot(FROM.plusDays(4),   1.0),
                        snapshot(FROM.plusDays(5), 100.0)
                ));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        assertThat(ctx.getMetrics().get(MetricType.DAILY_COMMITS_COUNT.name()).isAnomaly()).isTrue();
    }

    @Test
    void buildTeamContext_multiMember_aggregatesPerMember() {
        User alice = new User();
        alice.setUsername("alice");
        User bob = new User();
        bob.setUsername("bob");

        Team team = new Team();
        team.setName("Team Alpha");
        team.setMembers(new HashSet<>(Set.of(alice, bob)));

        when(metricSnapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                eq(alice), any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(
                        snapshot(FROM,             10.0),
                        snapshot(FROM.plusDays(1),  5.0)
                ));

        TeamMetricsContext ctx = service.buildTeamContext(team, FROM, TO);

        assertThat(ctx.getTeamName()).isEqualTo("Team Alpha");
        assertThat(ctx.getMembers()).hasSize(2);

        TeamMetricsContext.MemberMetrics aliceMetrics = ctx.getMembers().stream()
                .filter(m -> "alice".equals(m.getUsername()))
                .findFirst().orElseThrow();
        assertThat(aliceMetrics.getMetrics().get(MetricType.DAILY_COMMITS_COUNT.name())).isEqualTo(15.0);

        TeamMetricsContext.MemberMetrics bobMetrics = ctx.getMembers().stream()
                .filter(m -> "bob".equals(m.getUsername()))
                .findFirst().orElseThrow();
        assertThat(bobMetrics.getMetrics()).doesNotContainKey(MetricType.DAILY_COMMITS_COUNT.name());
    }

    /**
     * CONTEXT_METRIC_TYPES is declared explicitly to fix the order the model reads,
     * so it can no longer follow the inAiContext flag automatically. This guards the
     * two directions in which they can drift: a metric flagged but not listed would be
     * silently missing from every summary, and a metric listed but not flagged would be
     * sent to the model against the enum's own declaration.
     */
    @Test
    void contextMetricTypes_matchesInAiContextFlag_inBothDirections() {
        assertThat(AiContextBuilderService.CONTEXT_METRIC_TYPES)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(MetricType.values()).filter(t -> t.inAiContext).toList());
    }

    private MetricSnapshot snapshot(LocalDate date, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setDate(date);
        s.setValue(value);
        return s;
    }
}
