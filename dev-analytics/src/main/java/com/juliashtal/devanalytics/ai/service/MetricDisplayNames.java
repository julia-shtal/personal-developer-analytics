package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.metrics.model.MetricType;

import java.util.Map;

/**
 * Human-readable metric names as sent to the model, keyed by {@link MetricType}.
 * <p>Must stay in sync with the metric name mapping documented in {@link SystemPrompts}; this is
 * the reverse lookup {@link SummaryValidator} uses to confirm an insight names a metric the model
 * was actually given.</p>
 */
final class MetricDisplayNames {

    static final Map<MetricType, String> BY_TYPE = Map.ofEntries(
            Map.entry(MetricType.DAILY_COMMITS_COUNT, "Daily Commits"),
            Map.entry(MetricType.DAILY_PR_CREATED, "PRs Created"),
            Map.entry(MetricType.DAILY_PR_MERGED, "Merged PRs"),
            Map.entry(MetricType.DAILY_ISSUES_CREATED, "Issues Created"),
            Map.entry(MetricType.DAILY_ISSUES_CLOSED, "Issues Closed"),
            Map.entry(MetricType.DAILY_CHURN_RATIO, "Churn Ratio"),
            Map.entry(MetricType.PR_LEAD_TIME_HOURS_MEDIAN, "PR Lead Time"),
            Map.entry(MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN, "First Commit to Merge"),
            Map.entry(MetricType.ISSUE_LEAD_TIME_HOURS_MEDIAN, "Issue Lead Time"),
            Map.entry(MetricType.REVIEW_RESPONSE_TIME_HOURS_MEDIAN, "Review Response Time"),
            Map.entry(MetricType.FOCUS_RATIO_DAYS_TASKS, "Focus Ratio"),
            Map.entry(MetricType.REVIEW_PARTICIPATION_COUNT, "Review Participation"));

    private MetricDisplayNames() {
    }
}
