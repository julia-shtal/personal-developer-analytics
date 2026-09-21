package com.juliashtal.devanalytics.metrics.model;

/**
 * The supported metric types and their scope, aggregation and presentation properties.
 *
 * <p>The unit is declared here so a new metric cannot be added without one.</p>
 */
public enum MetricType {
    DAILY_COMMITS_COUNT(true, true, false, "commits"),
    DAILY_COMMITS_AVG_SIZE(false, false, false, "ln/commit"),
    DAILY_PR_CREATED(true, true, false, "prs"),
    DAILY_PR_MERGED(true, true, false, "prs"),
    DAILY_ISSUES_CREATED(true, true, false, "issues"),
    DAILY_ISSUES_CLOSED(true, true, false, "issues"),
    DAILY_CHURN_RATIO(true, false, false, "%"),
    PR_LEAD_TIME_HOURS_MEDIAN(true, false, true, "h"),
    ISSUE_LEAD_TIME_HOURS_MEDIAN(true, false, true, "h"),
    PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN(true, false, true, "h"),
    REVIEW_RESPONSE_TIME_HOURS_MEDIAN(true, false, true, "h"),
    FOCUS_RATIO_DAYS_TASKS(true, false, false, "%"),

    // Wellness signals
    /** Ratio of commits made outside 09:00–18:00 Mon–Fri in the user's timezone. */
    AFTER_HOURS_COMMIT_RATIO(false, false, false, "%"),
    /** Longest run of consecutive calendar days with ≥1 commit in the window. */
    DEEP_WORK_STREAK_DAYS(false, false, false, "d"),
    /** Max fraction of commits to a repo that belong only to this user (bus-factor proxy). */
    KNOWLEDGE_SILO_SCORE(false, false, false, "%"),

    // Code quality signals
    /** Fraction of commits where deletions > additions (refactoring indicator). */
    REFACTOR_RATIO(false, false, false, "%"),
    /** Median of (additions + deletions) / commitsCount per enriched merged PR (review complexity proxy). */
    PR_SIZE_COMPLEXITY_SCORE(false, false, false, "ln/c"),
    /** Fraction of merged PRs that had zero reviews. */
    MERGE_WITHOUT_REVIEW_RATIO(false, false, false, "%"),
    /** Average commits per ISO calendar week. */
    COMMITS_PER_WEEK_AVG(false, false, false, "/wk"),
    /** Count of distinct PRs the user reviewed (excluding self-reviews) in the calculation window. */
    REVIEW_PARTICIPATION_COUNT(true, false, true, "prs"),

    /** Median age in hours of the user's currently-open PRs (WIP queue signal). Point-in-time. */
    WIP_OPEN_PR_AGE_HOURS_MEDIAN(false, false, false, "h");

    /** True when this metric is included in the AI context for summary generation. */
    public final boolean inAiContext;
    /** True when the metric represents a daily count to be summed over the period (not averaged). */
    public final boolean dailySum;
    /** True when the metric is recomputed per ISO week; storage shape is a calculator property. */
    public final boolean aggregatePeriod;
    /** Display unit, as written in the CSV export column of the same name. */
    public final String unit;

    MetricType(boolean inAiContext, boolean dailySum, boolean aggregatePeriod, String unit) {
        this.inAiContext = inAiContext;
        this.dailySum = dailySum;
        this.aggregatePeriod = aggregatePeriod;
        this.unit = unit;
    }
}
