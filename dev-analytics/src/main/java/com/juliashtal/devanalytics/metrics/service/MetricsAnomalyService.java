package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

/**
 * Computes per-metric anomaly flags using the same 2σ rule as the AI context builder.
 * A metric is anomalous when any single observation deviates more than 2 standard deviations
 * from the mean of all observations in the window (requires ≥ 3 data points).
 */
@Service
@RequiredArgsConstructor
public class MetricsAnomalyService {

    /**
     * Mirrors {@code AiContextBuilderService.CONTEXT_METRIC_TYPES}. Declared here rather
     * than imported because {@code ai} depends on {@code metrics} and not the reverse;
     * {@code MetricTypeTest} asserts the two lists agree.
     */
    private static final List<MetricType> CONTEXT_METRIC_TYPES = List.of(
            DAILY_COMMITS_COUNT, DAILY_PR_CREATED, DAILY_PR_MERGED,
            DAILY_ISSUES_CREATED, DAILY_ISSUES_CLOSED, DAILY_CHURN_RATIO,
            PR_LEAD_TIME_HOURS_MEDIAN, PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
            ISSUE_LEAD_TIME_HOURS_MEDIAN, REVIEW_RESPONSE_TIME_HOURS_MEDIAN,
            REVIEW_PARTICIPATION_COUNT, FOCUS_RATIO_DAYS_TASKS
    );

    /** Minimum number of observations required before an anomaly check is meaningful. */
    private static final int ANOMALY_MIN_SAMPLE_SIZE = 3;
    /** A value more than this many standard deviations from the mean is flagged as anomalous. */
    private static final double ANOMALY_STD_DEV_THRESHOLD = 2.0;

    private final MetricSnapshotService metricSnapshotService;
    private final AggregateWindowResolver aggregateWindowResolver;

    /**
     * One flag per context metric. Period-stored metrics contribute one observation per
     * stored window rather than one per day, so the deviation being tested is week-to-week
     * variation. Which shape a metric is stored in is read off the rows, not off a list of
     * types — the list this class used to keep named four of the five aggregate types, and
     * the fifth was scored against an empty series.
     */
    public Map<MetricType, Boolean> computeAnomalies(User user, LocalDate from, LocalDate to) {
        Map<MetricType, Boolean> result = new LinkedHashMap<>();
        for (MetricType type : CONTEXT_METRIC_TYPES) {
            List<MetricSnapshot> rows =
                    metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeInWindow(user, type, from, to);

            List<MetricSnapshot> aggregateRows = AggregateWindowResolver.aggregateRows(rows);
            List<Double> values = aggregateRows.isEmpty()
                    ? AggregateWindowResolver.dailyRows(rows).stream()
                            .sorted(Comparator.comparing(MetricSnapshot::getDate))
                            .map(MetricSnapshot::getValue)
                            .collect(Collectors.toList())
                    : aggregateWindowResolver.perWindow(aggregateRows, type).stream()
                            .map(AggregateWindowResolver.WindowValue::value)
                            .collect(Collectors.toList());

            result.put(type, hasAnomaly(values));
        }
        return result;
    }

    private boolean hasAnomaly(List<Double> values) {
        int n = values.size();
        if (n < ANOMALY_MIN_SAMPLE_SIZE) return false;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        return values.stream().anyMatch(v -> Math.abs(v - mean) > ANOMALY_STD_DEV_THRESHOLD * stdDev);
    }
}
