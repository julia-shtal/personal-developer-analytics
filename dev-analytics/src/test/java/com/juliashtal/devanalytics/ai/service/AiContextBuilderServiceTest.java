package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.AggregatedMetricsContext;
import com.juliashtal.devanalytics.ai.model.TeamMetricsContext;
import com.juliashtal.devanalytics.ai.repository.GoalRepository;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.AggregateWindowResolver;
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

    /** A week the ISO-week grain actually produces: Monday 2024-01-01 to Sunday 2024-01-07. */
    private static final LocalDate WEEK1_FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate WEEK1_TO   = LocalDate.of(2024, 1, 7);
    private static final LocalDate WEEK2_FROM = LocalDate.of(2024, 1, 8);
    private static final LocalDate WEEK2_TO   = LocalDate.of(2024, 1, 14);

    @BeforeEach
    void setUp() {
        // The resolver is pure computation; a mock would make every assertion vacuous.
        service = new AiContextBuilderService(metricSnapshotService, new AggregateWindowResolver(), goalRepository);

        // Base: every query variant returns empty. Tests override the type-scoped variant they need.
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeInWindow(any(), any(), any(), any(), any()))
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
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(
                        dailySnapshot(FROM,              3.0),
                        dailySnapshot(FROM.plusDays(1),  5.0),
                        dailySnapshot(FROM.plusDays(2),  7.0),
                        dailySnapshot(FROM.plusDays(3),  9.0)
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
    void buildPersonalContext_aggregateMetricAcrossTwoWeeks_reducesOverPerWeekValues() {
        // Two ISO-week rows. Statistics describe variation across weeks, not across days:
        // min 20, max 40, median of {20, 40} = 30.
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.PR_LEAD_TIME_HOURS_MEDIAN), any(), any()))
                .thenReturn(List.of(
                        weekSnapshot(WEEK1_FROM, WEEK1_TO, 20.0),
                        weekSnapshot(WEEK2_FROM, WEEK2_TO, 40.0)
                ));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        AggregatedMetricsContext.MetricAggregate agg =
                ctx.getMetrics().get(MetricType.PR_LEAD_TIME_HOURS_MEDIAN.name());
        assertThat(agg).isNotNull();
        assertThat(agg.getMin()).isEqualTo("20");
        assertThat(agg.getMax()).isEqualTo("40");
        assertThat(agg.getMedian()).isEqualTo("30");
        assertThat(agg.getTotal()).isZero();   // a median is not a total
    }

    @Test
    void buildPersonalContext_reviewParticipationAcrossTwoWeeks_totalSumsTheWeeks() {
        // REVIEW_PARTICIPATION_COUNT is a count, so its windows add up even though the
        // enum's dailySum flag is false — the flag describes daily rows, not period rows.
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.REVIEW_PARTICIPATION_COUNT), any(), any()))
                .thenReturn(List.of(
                        weekSnapshot(WEEK1_FROM, WEEK1_TO, 4.0),
                        weekSnapshot(WEEK2_FROM, WEEK2_TO, 7.0)
                ));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        assertThat(ctx.getMetrics().get(MetricType.REVIEW_PARTICIPATION_COUNT.name()).getTotal())
                .isEqualTo(11L);
    }

    @Test
    void buildPersonalContext_crossRepoAggregateRowsInOneWeek_collapseToOneWeeklyValue() {
        // Two repositories, one week. The week contributes a single observation (the median
        // across repos), so the series is not inflated by how many repos the user has.
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.PR_LEAD_TIME_HOURS_MEDIAN), any(), any()))
                .thenReturn(List.of(
                        weekSnapshot(WEEK1_FROM, WEEK1_TO, 10.0),
                        weekSnapshot(WEEK1_FROM, WEEK1_TO, 30.0)
                ));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        AggregatedMetricsContext.MetricAggregate agg =
                ctx.getMetrics().get(MetricType.PR_LEAD_TIME_HOURS_MEDIAN.name());
        assertThat(agg.getMin()).isEqualTo("20");
        assertThat(agg.getMax()).isEqualTo("20");
        assertThat(agg.getMedian()).isEqualTo("20");
    }

    /**
     * The regression this task exists for.
     *
     * <p>The weekly summary job asks for a seven-day window it did not compute verbatim.
     * Under the old exact-period read, the five period-stored context metrics matched
     * nothing, {@code buildPersonalContext} only put a key in the map when the query came
     * back non-empty, and every scheduled summary was written from 7 of 12 metrics — the
     * whole flow and lead-time family plus review participation. Asserting the key set,
     * not just a few keys, is what would have caught it.
     */
    @Test
    void buildPersonalContext_mixedDailyAndWeeklyRows_containsEveryContextMetricType() {
        for (MetricType type : AiContextBuilderService.CONTEXT_METRIC_TYPES) {
            List<MetricSnapshot> rows = type.aggregatePeriod
                    ? List.of(weekSnapshot(WEEK1_FROM, WEEK1_TO, 6.0))   // written by the weekly pass
                    : List.of(dailySnapshot(FROM, 6.0));                 // written by the nightly pass
            when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                    any(), eq(type), any(), any())).thenReturn(rows);
        }

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, FROM.plusDays(6), null);

        assertThat(ctx.getMetrics().keySet())
                .containsExactlyElementsOf(AiContextBuilderService.CONTEXT_METRIC_TYPES.stream()
                        .map(MetricType::name).toList());
    }

    @Test
    void buildPersonalContext_withRepoScope_callsRepoScopedQueryAndSetsRepoName() {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(1L);
        repo.setName("my-repo");

        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), eq(repo), any(), any()))
                .thenReturn(List.of(dailySnapshot(FROM, 7.0)));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, repo);

        assertThat(ctx.getRepoName()).isEqualTo("my-repo");
        assertThat(ctx.getMetrics()).containsKey(MetricType.DAILY_COMMITS_COUNT.name());
        verify(metricSnapshotService, never())
                .getMetricSnapshotsByUserAndMetricTypeInWindow(any(), any(), any(), any());
    }

    @Test
    void buildPersonalContext_singleSnapshot_trendPctIsZero() {
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(dailySnapshot(FROM, 5.0)));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        AggregatedMetricsContext.MetricAggregate agg =
                ctx.getMetrics().get(MetricType.DAILY_COMMITS_COUNT.name());
        assertThat(agg.getTrendPct()).isEqualTo(0.0);
    }

    @Test
    void buildPersonalContext_twoSnapshots_anomalyIsFalse() {
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(
                        dailySnapshot(FROM,             1.0),
                        dailySnapshot(FROM.plusDays(1), 100.0)
                ));

        AggregatedMetricsContext ctx = service.buildPersonalContext(user, FROM, TO, null);

        assertThat(ctx.getMetrics().get(MetricType.DAILY_COMMITS_COUNT.name()).isAnomaly()).isFalse();
    }

    @Test
    void buildPersonalContext_outlierInSixValueSeries_anomalyIsTrue() {
        // mean≈17.5, stdDev≈36.9 → |100-17.5|=82.5 > 2*36.9=73.8 → anomaly=true
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(
                any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(
                        dailySnapshot(FROM,               1.0),
                        dailySnapshot(FROM.plusDays(1),   1.0),
                        dailySnapshot(FROM.plusDays(2),   1.0),
                        dailySnapshot(FROM.plusDays(3),   1.0),
                        dailySnapshot(FROM.plusDays(4),   1.0),
                        dailySnapshot(FROM.plusDays(5), 100.0)
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

        when(metricSnapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeInWindow(
                eq(alice), any(), eq(MetricType.DAILY_COMMITS_COUNT), any(), any()))
                .thenReturn(List.of(
                        dailySnapshot(FROM,             10.0),
                        dailySnapshot(FROM.plusDays(1),  5.0)
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
     * Team members' period-stored metrics used to be unreachable: there was no team-scoped
     * period query at all, and the four lead-time types silently returned nothing.
     */
    @Test
    void buildTeamContext_memberWithAggregateRows_includesThemInTheMemberMetrics() {
        User alice = new User();
        alice.setUsername("alice");
        Team team = new Team();
        team.setName("Team Alpha");
        team.setMembers(new HashSet<>(Set.of(alice)));

        when(metricSnapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeInWindow(
                eq(alice), any(), eq(MetricType.PR_LEAD_TIME_HOURS_MEDIAN), any(), any()))
                .thenReturn(List.of(
                        weekSnapshot(WEEK1_FROM, WEEK1_TO, 20.0),
                        weekSnapshot(WEEK2_FROM, WEEK2_TO, 40.0)
                ));

        TeamMetricsContext ctx = service.buildTeamContext(team, FROM, TO);

        assertThat(ctx.getMembers().get(0).getMetrics())
                .containsEntry(MetricType.PR_LEAD_TIME_HOURS_MEDIAN.name(), 30.0);
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

    /** DAILY shape: a calendar day, no period. */
    private MetricSnapshot dailySnapshot(LocalDate date, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setDate(date);
        s.setValue(value);
        return s;
    }

    /** AGGREGATE shape: dated at the window start, carrying the window it covers. */
    private MetricSnapshot weekSnapshot(LocalDate periodFrom, LocalDate periodTo, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setDate(periodFrom);
        s.setValue(value);
        s.setPeriodFrom(periodFrom);
        s.setPeriodTo(periodTo);
        return s;
    }
}
