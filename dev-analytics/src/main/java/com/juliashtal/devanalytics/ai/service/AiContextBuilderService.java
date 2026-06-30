package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.AggregatedMetricsContext;
import com.juliashtal.devanalytics.ai.model.GoalEntity;
import com.juliashtal.devanalytics.ai.model.GoalSummary;
import com.juliashtal.devanalytics.ai.model.TeamMetricsContext;
import com.juliashtal.devanalytics.ai.repository.GoalRepository;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
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

    private static final List<MetricType> CONTEXT_METRIC_TYPES = Arrays.stream(MetricType.values())
            .filter(t -> t.inAiContext).toList();

    private static final Set<MetricType> DAILY_SUM_METRICS = Arrays.stream(MetricType.values())
            .filter(t -> t.dailySum).collect(Collectors.toUnmodifiableSet());

    // FOCUS_RATIO_DAYS_TASKS is excluded: stored as per-day markers (periodFrom/To = null);
    // its aggregate is computed on the read side by counting markers in the date range.
    // Metrics stored with periodFrom/periodTo (not date-series) — must use exact-period query.
    private static final Set<MetricType> AGGREGATE_METRICS = Arrays.stream(MetricType.values())
            .filter(t -> t.aggregatePeriod).collect(Collectors.toUnmodifiableSet());

    /** Minimum observations required before an anomaly check is meaningful. */
    private static final int ANOMALY_MIN_SAMPLE_SIZE = 3;
    /** A value more than this many standard deviations from the mean is anomalous. */
    private static final double ANOMALY_STD_DEV_THRESHOLD = 2.0;

    private final MetricSnapshotService metricSnapshotService;
    private final GoalRepository goalRepository;

    public AggregatedMetricsContext buildPersonalContext(User user, LocalDate from, LocalDate to,
                                                         GitRepositoryEntity repo) {
        Map<String, AggregatedMetricsContext.MetricAggregate> aggregates = new LinkedHashMap<>();

        for (MetricType type : CONTEXT_METRIC_TYPES) {
            List<MetricSnapshot> snapshots;
            boolean isAggregate = AGGREGATE_METRICS.contains(type);
            if (repo != null) {
                snapshots = isAggregate
                        ? metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo(user, type, repo, from, to)
                        : metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(user, type, repo, from, to);
            } else {
                snapshots = isAggregate
                        ? metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(user, type, from, to)
                        : metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, type, from, to);
            }

            if (!snapshots.isEmpty()) {
                List<Double> values = snapshots.stream()
                        .sorted(Comparator.comparing(MetricSnapshot::getDate))
                        .map(MetricSnapshot::getValue)
                        .toList();
                aggregates.put(type.name(), computeAggregate(values, DAILY_SUM_METRICS.contains(type)));
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
            // NOTE: aggregate-period metrics (aggregatePeriod=true) are not supported here —
            // MetricSnapshotService has no team-scoped period-exact query. Those four types
            // (PR_LEAD_TIME_HOURS_MEDIAN, ISSUE_LEAD_TIME_HOURS_MEDIAN, etc.) return no data.
            // This is a pre-existing limitation carried over from MetricsAiService unchanged.
            for (MetricType type : CONTEXT_METRIC_TYPES) {
                List<MetricSnapshot> snapshots = metricSnapshotService
                        .getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(member, team, type, from, to);
                if (!snapshots.isEmpty()) {
                    double value = DAILY_SUM_METRICS.contains(type)
                            ? snapshots.stream().mapToDouble(MetricSnapshot::getValue).sum()
                            : snapshots.stream().mapToDouble(MetricSnapshot::getValue).average().orElse(0.0);
                    aggregated.put(type.name(), value);
                }
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
