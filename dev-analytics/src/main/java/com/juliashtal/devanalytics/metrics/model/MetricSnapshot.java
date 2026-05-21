package com.juliashtal.devanalytics.metrics.model;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Persisted output of one metric calculation for one user, one day, and one optional scope
 * (repository and/or team). The table stores two structurally distinct row shapes that share
 * the same columns; they must not be mixed in aggregate queries.
 *
 * <h3>Shape 1 — DAILY</h3>
 * <p>Used by metrics whose natural granularity is a single calendar day:
 * {@code DAILY_COMMITS_COUNT}, {@code DAILY_COMMITS_AVG_SIZE}, {@code DAILY_PR_CREATED},
 * {@code DAILY_PR_MERGED}, {@code DAILY_ISSUES_CREATED}, {@code DAILY_ISSUES_CLOSED},
 * {@code DAILY_CHURN_RATIO}, {@code FOCUS_RATIO_DAYS_TASKS}.</p>
 * <ul>
 *   <li>{@link #date} — the calendar day being measured.</li>
 *   <li>{@link #periodFrom} — {@code NULL}.</li>
 *   <li>{@link #periodTo} — {@code NULL}.</li>
 *   <li>{@link #value} — the metric value for that single day (count, ratio, etc.).</li>
 * </ul>
 * <p>Invariant: {@code period_from IS NULL AND period_to IS NULL}.</p>
 *
 * <h3>Shape 2 — AGGREGATE</h3>
 * <p>Used by window-based metrics that aggregate over a date range:
 * {@code PR_LEAD_TIME_HOURS_MEDIAN}, {@code ISSUE_LEAD_TIME_HOURS_MEDIAN},
 * {@code PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN},
 * {@code REVIEW_RESPONSE_TIME_HOURS_MEDIAN}, {@code AFTER_HOURS_COMMIT_RATIO},
 * {@code DEEP_WORK_STREAK_DAYS}, {@code KNOWLEDGE_SILO_SCORE}, {@code REFACTOR_RATIO},
 * {@code PR_SIZE_COMPLEXITY_SCORE}, {@code MERGE_WITHOUT_REVIEW_RATIO},
 * {@code MERGE_TO_MAIN_FREQUENCY_PER_WEEK}.</p>
 * <ul>
 *   <li>{@link #date} — the snapshot capture date (when the calculation ran), not the
 *       start or end of the window.</li>
 *   <li>{@link #periodFrom} — inclusive start of the calculation window.</li>
 *   <li>{@link #periodTo} — inclusive end of the calculation window.</li>
 *   <li>{@link #value} — the aggregated metric value (median hours, ratio, score, etc.).</li>
 * </ul>
 * <p>Invariant: {@code period_from IS NOT NULL AND period_to IS NOT NULL}.</p>
 *
 * <h3>Upsert guard</h3>
 * <p>All writes go through {@code MetricsService.saveMetric}, which uses a native-SQL
 * {@code findExisting} query with {@code IS NOT DISTINCT FROM} on nullable dimensions
 * ({@code repository_id}, {@code team_id}, {@code period_from}, {@code period_to}) to
 * locate an existing row before inserting. Bypassing {@code saveMetric} will produce
 * duplicate rows that aggregate incorrectly.</p>
 *
 * <h3>Scope</h3>
 * <p>Personal metrics: {@link #team} is {@code NULL}.
 * Team-scoped metrics: {@link #team} is set.
 * Both shapes can appear with either scope.</p>
 */
@Data
@Entity
@Table(
        name = "metric_snapshots",
        indexes = {
                @Index(name = "ix_metric_user_repo_date_type", columnList = "user_id, repository_id, date, metricType")
        }
)
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepositoryEntity repository;

    /** DAILY shape: the calendar day measured. AGGREGATE shape: the snapshot capture date. */
    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private MetricType metricType;

    @Column(nullable = false)
    private double value;

    /** NULL for DAILY rows. Inclusive start of the calculation window for AGGREGATE rows. */
    @Column(name = "period_from")
    private LocalDate periodFrom;

    /** NULL for DAILY rows. Inclusive end of the calculation window for AGGREGATE rows. */
    @Column(name = "period_to")
    private LocalDate periodTo;
}
