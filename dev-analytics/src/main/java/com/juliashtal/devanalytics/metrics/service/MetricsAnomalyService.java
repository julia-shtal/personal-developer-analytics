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

    // Mirrors MetricsAiService.CONTEXT_METRIC_TYPES — keep in sync if AI context changes.
    private static final List<MetricType> CONTEXT_METRIC_TYPES = List.of(
            DAILY_COMMITS_COUNT, DAILY_PR_CREATED, DAILY_PR_MERGED,
            DAILY_ISSUES_CREATED, DAILY_ISSUES_CLOSED, DAILY_CHURN_RATIO,
            PR_LEAD_TIME_HOURS_MEDIAN, PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
            ISSUE_LEAD_TIME_HOURS_MEDIAN, REVIEW_RESPONSE_TIME_HOURS_MEDIAN,
            FOCUS_RATIO_DAYS_TASKS
    );

    // Aggregate metrics use periodFrom/periodTo queries instead of date-series queries.
    private static final Set<MetricType> AGGREGATE_METRICS = Set.of(
            PR_LEAD_TIME_HOURS_MEDIAN, PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
            ISSUE_LEAD_TIME_HOURS_MEDIAN, REVIEW_RESPONSE_TIME_HOURS_MEDIAN
    );

    private final MetricSnapshotService metricSnapshotService;

    public Map<MetricType, Boolean> computeAnomalies(User user, LocalDate from, LocalDate to) {
        Map<MetricType, Boolean> result = new LinkedHashMap<>();
        for (MetricType type : CONTEXT_METRIC_TYPES) {
            List<MetricSnapshot> snapshots = AGGREGATE_METRICS.contains(type)
                    ? metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(user, type, from, to)
                    : metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, type, from, to);

            List<Double> values = snapshots.stream()
                    .sorted(Comparator.comparing(MetricSnapshot::getDate))
                    .map(MetricSnapshot::getValue)
                    .collect(Collectors.toList());
            result.put(type, hasAnomaly(values));
        }
        return result;
    }

    private boolean hasAnomaly(List<Double> values) {
        int n = values.size();
        if (n < 3) return false;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        return values.stream().anyMatch(v -> Math.abs(v - mean) > 2 * stdDev);
    }
}
