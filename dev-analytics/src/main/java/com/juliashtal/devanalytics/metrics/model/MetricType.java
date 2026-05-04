package com.juliashtal.devanalytics.metrics.model;

public enum MetricType {
    DAILY_COMMITS_COUNT, DAILY_COMMITS_AVG_SIZE,
    DAILY_PR_CREATED, DAILY_PR_MERGED,
    DAILY_ISSUES_CREATED, DAILY_ISSUES_CLOSED,
    DAILY_CHURN_RATIO,
    PR_LEAD_TIME_HOURS_MEDIAN,
    ISSUE_LEAD_TIME_HOURS_MEDIAN,
    PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
    REVIEW_RESPONSE_TIME_HOURS_MEDIAN,
    FOCUS_RATIO_DAYS_TASKS,

    // Wellness signals
    /** Ratio of commits made outside 09:00–18:00 Mon–Fri in the user's timezone. */
    AFTER_HOURS_COMMIT_RATIO,
    /** Longest run of consecutive calendar days with ≥1 commit in the window. */
    DEEP_WORK_STREAK_DAYS,
    /** Max fraction of commits to a repo that belong only to this user (bus-factor proxy). */
    KNOWLEDGE_SILO_SCORE,

    // Code quality signals
    /** Fraction of commits where deletions > additions (refactoring indicator). */
    REFACTOR_RATIO,
    /** Median of (additions + deletions) / commitsCount per merged PR (review complexity proxy). */
    PR_SIZE_COMPLEXITY_SCORE,
    /** Fraction of merged PRs that had zero reviews. */
    MERGE_WITHOUT_REVIEW_RATIO,
    /** Average commits per ISO calendar week (DORA deployment-frequency proxy). */
    MERGE_TO_MAIN_FREQUENCY_PER_WEEK
}
