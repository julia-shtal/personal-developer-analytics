package com.juliashtal.devanalytics.metrics.model;

/**
 * The supported metric types and their scope and aggregation flags.
 */
public enum MetricType {
    DAILY_COMMITS_COUNT(true, true, false),
    DAILY_COMMITS_AVG_SIZE(false, false, false),
    DAILY_PR_CREATED(true, true, false),
    DAILY_PR_MERGED(true, true, false),
    DAILY_ISSUES_CREATED(true, true, false),
    DAILY_ISSUES_CLOSED(true, true, false),
    DAILY_CHURN_RATIO(true, false, false),
    PR_LEAD_TIME_HOURS_MEDIAN(true, false, true),
    ISSUE_LEAD_TIME_HOURS_MEDIAN(true, false, true),
    PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN(true, false, true),
    REVIEW_RESPONSE_TIME_HOURS_MEDIAN(true, false, true),
    FOCUS_RATIO_DAYS_TASKS(true, false, false),

    // Wellness signals
    /** Ratio of commits made outside 09:00–18:00 Mon–Fri in the user's timezone. */
    AFTER_HOURS_COMMIT_RATIO(false, false, false),
    /** Longest run of consecutive calendar days with ≥1 commit in the window. */
    DEEP_WORK_STREAK_DAYS(false, false, false),
    /** Max fraction of commits to a repo that belong only to this user (bus-factor proxy). */
    KNOWLEDGE_SILO_SCORE(false, false, false),

    // Code quality signals
    /** Fraction of commits where deletions > additions (refactoring indicator). */
    REFACTOR_RATIO(false, false, false),
    /** Median of (additions + deletions) / commitsCount per merged PR (review complexity proxy). */
    PR_SIZE_COMPLEXITY_SCORE(false, false, false),
    /** Fraction of merged PRs that had zero reviews. */
    MERGE_WITHOUT_REVIEW_RATIO(false, false, false),
    /** Average commits per ISO calendar week (DORA deployment-frequency proxy). */
    MERGE_TO_MAIN_FREQUENCY_PER_WEEK(false, false, false),
    /** Count of distinct PRs the user reviewed (excluding self-reviews) in the calculation window. */
    REVIEW_PARTICIPATION_COUNT(true, false, true),

    /** Median age in hours of the user's currently-open PRs (WIP queue signal). Point-in-time. */
    WIP_OPEN_PR_AGE_HOURS_MEDIAN(false, false, false);

    /** True when this metric is included in the AI context for summary generation. */
    public final boolean inAiContext;
    /** True when the metric represents a daily count to be summed over the period (not averaged). */
    public final boolean dailySum;
    /** True when the metric is stored with periodFrom/periodTo instead of a date series. */
    public final boolean aggregatePeriod;

    MetricType(boolean inAiContext, boolean dailySum, boolean aggregatePeriod) {
        this.inAiContext = inAiContext;
        this.dailySum = dailySum;
        this.aggregatePeriod = aggregatePeriod;
    }
}
