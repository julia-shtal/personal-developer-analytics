package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.AggregatedMetricsContext;
import com.juliashtal.devanalytics.ai.model.GoalEntity;
import com.juliashtal.devanalytics.ai.model.GoalSummary;
import com.juliashtal.devanalytics.ai.model.TeamMetricsContext;
import com.juliashtal.devanalytics.ai.repository.GoalRepository;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.AggregateWindowResolver;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the metrics context objects consumed by {@link MetricsAiService}.
 *
 * <p>Dependencies: {@code metrics} package (snapshots, types) and {@code git}/{@code user} models
 * for scoping. Deliberately free of LLM, prompt, and persistence concerns so context-building
 * and statistical logic can be unit-tested in isolation.
 */
@Service
@RequiredArgsConstructor
public class AiContextBuilderService {

    /**
     * Metric types supplied to the model, in a fixed presentation order:
     * activity, flow and lead time, collaboration, wellness. The order reaches
     * the prompt (LinkedHashMap -> JSON -> prompt text), so it is declared here
     * rather than derived from MetricType.values(), whose order is a property of
     * the enum declaration and not a decision about what the model reads.
     *
     * <p>Package-private so {@code AiContextBuilderServiceTest} can assert that this
     * list and the {@code inAiContext} flag never drift apart.
     */
    static final List<MetricType> CONTEXT_METRIC_TYPES = List.of(
            // Activity
            MetricType.DAILY_COMMITS_COUNT,
            MetricType.DAILY_PR_CREATED,
            MetricType.DAILY_PR_MERGED,
            MetricType.DAILY_ISSUES_CREATED,
            MetricType.DAILY_ISSUES_CLOSED,
            MetricType.DAILY_CHURN_RATIO,
            // Flow and lead time
            MetricType.PR_LEAD_TIME_HOURS_MEDIAN,
            MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
            MetricType.ISSUE_LEAD_TIME_HOURS_MEDIAN,
            MetricType.REVIEW_RESPONSE_TIME_HOURS_MEDIAN,
            // Collaboration
            MetricType.REVIEW_PARTICIPATION_COUNT,
            // Wellness
            MetricType.FOCUS_RATIO_DAYS_TASKS);

    private static final Set<MetricType> DAILY_SUM_METRICS = Arrays.stream(MetricType.values())
            .filter(t -> t.dailySum).collect(Collectors.toUnmodifiableSet());

    /** Minimum observations required before an anomaly check is meaningful. */
    private static final int ANOMALY_MIN_SAMPLE_SIZE = 3;
    /** A value more than this many standard deviations from the mean is anomalous. */
    private static final double ANOMALY_STD_DEV_THRESHOLD = 2.0;

    private final MetricSnapshotService metricSnapshotService;
    private final AggregateWindowResolver aggregateWindowResolver;
    private final GoalRepository goalRepository;

    public AggregatedMetricsContext buildPersonalContext(User user, LocalDate from, LocalDate to,
                                                         GitRepositoryEntity repo) {
        Map<String, AggregatedMetricsContext.MetricAggregate> aggregates = new LinkedHashMap<>();

        for (MetricType type : CONTEXT_METRIC_TYPES) {
            // One query per type, routed by the shape of the rows it comes back with rather
            // than by a list of which types are period-stored. The exact-period query this
            // replaced matched only windows that had been passed verbatim to the calculation
            // endpoint, so the weekly summary job — which computes nothing itself — found no
            // rows for any period-stored metric and dropped it from the context silently.
            List<MetricSnapshot> rows = repo != null
                    ? metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(user, type, repo, from, to)
                    : metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(user, type, from, to);

            List<Double> values = valuesForContext(rows, type);
            if (!values.isEmpty()) {
                boolean isSum = DAILY_SUM_METRICS.contains(type) || aggregateWindowResolver.isCount(type);
                aggregates.put(type.name(), computeAggregate(values, isSum));
            }
        }

        AggregatedMetricsContext ctx = new AggregatedMetricsContext();
        ctx.setFrom(from);
        ctx.setTo(to);
        ctx.setRepoName(repo != null ? repo.getName() : null);
        ctx.setMetrics(aggregates);

        // Attach active goals — target date >= today so past-due goals are excluded
        List<GoalEntity> activeGoalEntities =
                goalRepository.findByUser_IdAndTargetDateGreaterThanEqual(user.getId(), LocalDate.now());

        List<GoalSummary> goalSummaries = activeGoalEntities.stream().map(goal -> {
            MetricType type;
            try { type = MetricType.valueOf(goal.getMetricType()); }
            catch (IllegalArgumentException e) { return null; }
            List<MetricSnapshot> snaps = metricSnapshotService
                    .getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, type, from, to);
            Double current = snaps.isEmpty() ? null
                    : snaps.stream().mapToDouble(MetricSnapshot::getValue).average().orElse(0.0);
            return new GoalSummary(goal.getMetricType(), goal.getTargetValue(), goal.getTargetDate(), current);
        }).filter(Objects::nonNull).toList();

        ctx.setActiveGoals(goalSummaries);
        return ctx;
    }

    public TeamMetricsContext buildTeamContext(Team team, LocalDate from, LocalDate to) {
        List<TeamMetricsContext.MemberMetrics> memberMetricsList = new ArrayList<>();

        for (User member : team.getMembers()) {
            TeamMetricsContext.MemberMetrics mm = new TeamMetricsContext.MemberMetrics();
            mm.setUsername(member.getUsername());

            Map<String, Double> aggregated = new LinkedHashMap<>();
            for (MetricType type : CONTEXT_METRIC_TYPES) {
                List<MetricSnapshot> rows = metricSnapshotService
                        .getMetricSnapshotsByUserAndTeamAndMetricTypeInWindow(member, team, type, from, to);

                // Daily rows sum or average as before; period rows now resolve through the
                // window resolver instead of being dropped for want of a team-scoped query.
                List<MetricSnapshot> dailyRows = AggregateWindowResolver.dailyRows(rows);
                if (!dailyRows.isEmpty()) {
                    double value = DAILY_SUM_METRICS.contains(type)
                            ? dailyRows.stream().mapToDouble(MetricSnapshot::getValue).sum()
                            : dailyRows.stream().mapToDouble(MetricSnapshot::getValue).average().orElse(0.0);
                    aggregated.put(type.name(), value);
                }
                aggregateWindowResolver.resolve(AggregateWindowResolver.aggregateRows(rows), type)
                        .ifPresent(r -> aggregated.put(type.name(), r.value()));
            }
            mm.setMetrics(aggregated);
            memberMetricsList.add(mm);
        }

        TeamMetricsContext ctx = new TeamMetricsContext();
        ctx.setFrom(from);
        ctx.setTo(to);
        ctx.setTeamName(team.getName());
        ctx.setMemberCount(team.getMembers().size());
        ctx.setMembers(memberMetricsList);
        return ctx;
    }

    /**
     * The chronological series the statistics are computed over.
     *
     * <p>DAILY rows contribute one value per day, ordered by date. AGGREGATE rows
     * contribute one value per stored window, ordered by window start, with
     * cross-repository rows already combined — so min, max, median, trend and anomaly
     * describe variation across weeks rather than across repositories. A type only ever
     * writes one shape, so exactly one branch contributes.
     */
    private List<Double> valuesForContext(List<MetricSnapshot> rows, MetricType type) {
        List<MetricSnapshot> aggregateRows = AggregateWindowResolver.aggregateRows(rows);
        if (!aggregateRows.isEmpty()) {
            return aggregateWindowResolver.perWindow(aggregateRows, type).stream()
                    .map(AggregateWindowResolver.WindowValue::value)
                    .toList();
        }
        return AggregateWindowResolver.dailyRows(rows).stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricSnapshot::getValue)
                .toList();
    }

    private AggregatedMetricsContext.MetricAggregate computeAggregate(List<Double> values, boolean isSumMetric) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();

        double min    = sorted.get(0);
        double max    = sorted.get(n - 1);
        double median = n % 2 == 1
                ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        long total = isSumMetric
                ? Math.round(values.stream().mapToDouble(Double::doubleValue).sum())
                : 0L;

        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.HALF_UP);

        return AggregatedMetricsContext.MetricAggregate.builder()
                .min(df.format(min))
                .max(df.format(max))
                .median(df.format(median))
                .total(total)
                .trendPct(computeTrendPct(values))
                .anomaly(hasAnomaly(values))
                .build();
    }

    private double computeTrendPct(List<Double> chronological) {
        int n = chronological.size();
        if (n < 2) return 0.0;
        int half = n / 2;
        double earlyAvg  = chronological.subList(0, half).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double recentAvg = chronological.subList(n - half, n).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (earlyAvg == 0) return 0.0;
        return Math.round(((recentAvg - earlyAvg) / earlyAvg) * 1000.0) / 10.0;
    }

    private boolean hasAnomaly(List<Double> values) {
        int n = values.size();
        if (n < ANOMALY_MIN_SAMPLE_SIZE) return false;
        double mean     = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        double stdDev   = Math.sqrt(variance);
        return values.stream().anyMatch(v -> Math.abs(v - mean) > ANOMALY_STD_DEV_THRESHOLD * stdDev);
    }
}
