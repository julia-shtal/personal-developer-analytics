package com.juliashtal.devanalytics.metrics.controller;

import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.metrics.model.*;
import com.juliashtal.devanalytics.metrics.service.AggregateWindowResolver;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillService;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

/**
 * REST controller for personal metrics.
 * Mounted at /api/metrics — snapshots, aggregates, and recalculation.
 */
@RestController
@RequestMapping("/api/metrics")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricSnapshotService metricSnapshotService;
    private final MetricsService metricsService;
    private final MetricsAnomalyService metricsAnomalyService;
    private final AggregateWindowResolver aggregateWindowResolver;
    private final RepoService repoService;
    private final CheckHelper checkHelper;
    private final MetricBackfillService metricBackfillService;

    // =========================================================================
    // Personal endpoints — team IS NULL snapshots only
    // =========================================================================

    @Operation(summary = "Calculate and persist all personal daily metrics for a date range")
    @PostMapping("/calculate")
    public void calculate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User user = checkHelper.currentUser();
        metricsService.calculateDailyMetrics(user.getId(), from, to);
    }

    @Operation(summary = "Daily Commits Count series for the current user")
    @GetMapping("/daily-commits-count")
    public List<MetricPointDto> getDailyCommits(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_COMMITS_COUNT, from, to, repoId);
    }

    @Operation(summary = "Daily PRs Created series for the current user")
    @GetMapping("/daily-pr-created")
    public List<MetricPointDto> getDailyPrCreated(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_PR_CREATED, from, to, repoId);
    }

    @Operation(summary = "Daily PRs Merged series for the current user")
    @GetMapping("/daily-pr-merged")
    public List<MetricPointDto> getDailyPrMerged(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_PR_MERGED, from, to, repoId);
    }

    @Operation(summary = "Daily Issues Closed series for the current user")
    @GetMapping("/daily-issues-closed")
    public List<MetricPointDto> getDailyIssuesClosed(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_ISSUES_CLOSED, from, to, repoId);
    }

    @Operation(summary = "Daily Issues Created series for the current user")
    @GetMapping("/daily-issues-created")
    public List<MetricPointDto> getDailyIssuesCreated(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_ISSUES_CREATED, from, to, repoId);
    }

    @Operation(summary = "Daily Churn Ratio series for the current user")
    @GetMapping("/daily-churn-ratio")
    public List<MetricPointDto> getDailyChurn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_CHURN_RATIO, from, to, repoId);
    }

    @Operation(summary = "PR Lead Time (median hours) for the current user")
    @GetMapping("/pr-lead-time")
    public MetricAggregateDto getPrLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(PR_LEAD_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @Operation(summary = "PR First-Commit-to-Merge Lead Time (median hours) for the current user")
    @GetMapping("/pr-first-commit-to-merge-lead-time")
    public MetricAggregateDto getPrFirstCommitLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @Operation(summary = "Review Response Time (median hours) for the current user")
    @GetMapping("/review-response-time")
    public MetricAggregateDto getReviewResponseTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(REVIEW_RESPONSE_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @Operation(summary = "Issue Lead Time (median hours) for the current user")
    @GetMapping("/issue-lead-time")
    public MetricAggregateDto getIssueLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(ISSUE_LEAD_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @Operation(summary = "Daily Focus Ratio (days with tasks) series for the current user")
    @GetMapping("/focus-ratio/series")
    public List<MetricPointDto> getFocusRatioSeries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User user = checkHelper.currentUser();
        return metricSnapshotService
                .getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, FOCUS_RATIO_DAYS_TASKS, from, to)
                .stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    @Operation(summary = "Focus Ratio (days with tasks) aggregated over weekdays in the date range")
    @GetMapping("/focus-ratio")
    public MetricAggregateDto getFocusRatioAggregate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User user = checkHelper.currentUser();
        long activeDays = metricSnapshotService
                .getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, FOCUS_RATIO_DAYS_TASKS, from, to)
                .size();

        // Count total weekdays in the requested window (Mon–Fri only)
        long totalWeekdays = from.datesUntil(to.plusDays(1))
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY
                          && d.getDayOfWeek() != DayOfWeek.SUNDAY)
                .count();

        double ratio = totalWeekdays > 0 ? (double) activeDays / totalWeekdays : 0.0;
        return new MetricAggregateDto(FOCUS_RATIO_DAYS_TASKS, ratio, null, null);
    }

    // =========================================================================
    // Wellness + Quality metric endpoints (personal, aggregate)
    // =========================================================================

    @Operation(summary = "After-Hours Commit Ratio for the current user")
    @GetMapping("/after-hours-commit-ratio")
    public MetricAggregateDto getAfterHoursRatio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(AFTER_HOURS_COMMIT_RATIO, from, to, null);
    }

    @Operation(summary = "Refactor Ratio for the current user")
    @GetMapping("/refactor-ratio")
    public MetricAggregateDto getRefactorRatio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(REFACTOR_RATIO, from, to, null);
    }

    @Operation(summary = "Deep Work Streak (longest consecutive run of days) for the current user")
    @GetMapping("/deep-work-streak")
    public MetricAggregateDto getDeepWorkStreak(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(DEEP_WORK_STREAK_DAYS, from, to, null);
    }

    @Operation(summary = "Commits per Week (average commits per ISO week) for the current user")
    @GetMapping("/commits-per-week-avg")
    public MetricAggregateDto getCommitsPerWeekAvg(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(COMMITS_PER_WEEK_AVG, from, to, null);
    }

    @Operation(summary = "Knowledge Silo Score for the current user")
    @GetMapping("/knowledge-silo-score")
    public MetricAggregateDto getKnowledgeSilo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(KNOWLEDGE_SILO_SCORE, from, to, repoId);
    }

    @Operation(summary = "PR Size Complexity Score (median changed lines per commit across merged PRs) for the current user")
    @GetMapping("/pr-size-complexity")
    public MetricAggregateDto getPrSizeComplexity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(PR_SIZE_COMPLEXITY_SCORE, from, to, repoId);
    }

    @Operation(summary = "WIP Open PR Age (median hours of currently open PRs) for the current user")
    @GetMapping("/wip-open-pr-age")
    public MetricAggregateDto getWipOpenPrAge(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(WIP_OPEN_PR_AGE_HOURS_MEDIAN, from, to, repoId);
    }

    @Operation(summary = "Merge Without Review Ratio for the current user")
    @GetMapping("/merge-without-review-ratio")
    public MetricAggregateDto getMergeWithoutReview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(MERGE_WITHOUT_REVIEW_RATIO, from, to, repoId);
    }

    @Operation(summary = "Code Review Participation (distinct PRs reviewed) for the current user")
    @GetMapping("/review-participation")
    public MetricAggregateDto getReviewParticipation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(REVIEW_PARTICIPATION_COUNT, from, to, null);
    }

    // =========================================================================
    // Backfill + freshness
    // =========================================================================

    /**
     * Manually trigger a personal metrics backfill for an explicit date range.
     * Self-scoped: always computes for the authenticated user only.
     * Useful after connecting a new data source and wanting historical metrics.
     */
    @Operation(summary = "Manually backfill personal metrics for a past date range")
    @PostMapping("/backfill")
    public ResponseEntity<Void> backfill(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (from.isAfter(to)) throw new BadRequestException("'from' must not be after 'to'");
        if (to.isAfter(LocalDate.now().minusDays(1))) throw new BadRequestException("Cannot backfill future dates");
        User user = checkHelper.currentUser();
        metricsService.calculateDailyMetrics(user.getId(), from, to);
        return ResponseEntity.accepted().build();
    }

    /**
     * Returns per-metric anomaly flags for the current user over the requested window.
     * A metric is anomalous when any daily observation deviates more than 2σ from the window mean.
     * Only the 11 AI context metrics are evaluated; metrics with fewer than 3 data points return false.
     */
    @Operation(summary = "Per-metric anomaly flags (>2 sigma deviation from window mean) for the current user")
    @GetMapping("/anomalies")
    public Map<String, Boolean> getAnomalies(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User user = checkHelper.currentUser();
        return metricsAnomalyService.computeAnomalies(user, from, to)
                .entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
    }

    /**
     * Returns how far the current user's personal metrics have been computed: the latest
     * computed day, the range the backfill targets, and how many days inside it are still
     * outstanding.
     *
     * <p>Read-only. The backfill itself never runs in a request thread — this calls
     * {@code describeCoverage}, not {@code backfillUser}.
     */
    @Operation(summary = "Coverage of the current user's computed personal metrics")
    @GetMapping("/freshness")
    public MetricsFreshnessDto getFreshness() {
        User user = checkHelper.currentUser();
        BackfillResult coverage = metricBackfillService.describeCoverage(user.getId());
        return new MetricsFreshnessDto(
                metricSnapshotService.findMaxPersonalDate(user.getId()).orElse(null),
                coverage.coverageFrom(),
                coverage.coverageTo(),
                coverage.daysRemaining());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private List<MetricPointDto> getPersonalDailySeries(MetricType type, LocalDate from, LocalDate to, Long repoId) {
        User user = checkHelper.currentUser();

        if (repoId == null) {
            // DAILY_CHURN_RATIO is a ratio: averaging per-repo daily values gives the correct cross-repo estimate.
            // All other daily-series metrics are counts: summing per-repo values is correct.
            boolean isRatio = type == DAILY_CHURN_RATIO;

            Map<LocalDate, List<Double>> valuesByDate = new TreeMap<>();
            metricSnapshotService
                    .getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, type, from, to)
                    .forEach(s -> valuesByDate
                            .computeIfAbsent(s.getDate(), k -> new ArrayList<>())
                            .add(s.getValue()));

            return valuesByDate.entrySet().stream()
                    .map(e -> {
                        List<Double> vals = e.getValue();
                        double agg = isRatio
                                ? vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)
                                : vals.stream().mapToDouble(Double::doubleValue).sum();
                        return new MetricPointDto(e.getKey(), agg, type.name(), null, null);
                    })
                    .toList();
        }

        // Entitlement check, not a lookup: getById would answer for any id in the database.
        GitRepositoryEntity repo = repoService.getAccessibleRepo(user.getId(), repoId);
        return metricSnapshotService
                .getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(user, type, repo, from, to)
                .stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    /**
     * Resolves a period-stored metric over the requested window.
     *
     * <p>Stored windows follow the calculation grain, almost never the window a dashboard asks
     * for, so this reads every stored window the request contains and combines them the way that
     * metric permits. The returned {@code periodFrom}/{@code periodTo} come from the resolved
     * rows, never from the request, so a figure is never labelled with a window it was not
     * computed over.</p>
     */
    private MetricAggregateDto getPersonalLeadTimeAggregate(MetricType type, LocalDate from, LocalDate to, Long repoId) {
        User user = checkHelper.currentUser();
        List<MetricSnapshot> rows;

        if (repoId == null) {
            rows = metricSnapshotService
                    .getMetricSnapshotsByUserAndMetricTypeInWindow(user, type, from, to);
        } else {
            GitRepositoryEntity repo = repoService.getAccessibleRepo(user.getId(), repoId);
            rows = metricSnapshotService
                    .getMetricSnapshotsByUserAndMetricTypeAndRepositoryInWindow(user, type, repo, from, to);
        }

        return aggregateWindowResolver.resolve(AggregateWindowResolver.aggregateRows(rows), type)
                .map(r -> new MetricAggregateDto(type, r.value(), r.periodFrom(), r.periodTo()))
                .orElseGet(() -> new MetricAggregateDto(type, 0.0, null, null));
    }

}
