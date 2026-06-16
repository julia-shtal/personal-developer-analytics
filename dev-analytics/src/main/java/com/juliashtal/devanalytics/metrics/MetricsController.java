package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.metrics.model.*;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
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

@RestController
@RequestMapping("/api/metrics")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricSnapshotService metricSnapshotService;
    private final MetricsService metricsService;
    private final MetricsAnomalyService metricsAnomalyService;
    private final RepoService repoService;
    private final TeamService teamService;
    private final UserService userService;
    private final CheckHelper checkHelper;

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
    @GetMapping("/daily-commits")
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
    @GetMapping("/daily-churn")
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
    @GetMapping("/pr-first-commit-lead-time")
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
    @GetMapping("/after-hours-ratio")
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

    @Operation(summary = "Merge Frequency (merges to main per ISO week, average) for the current user")
    @GetMapping("/merge-to-main-frequency")
    public MetricAggregateDto getMergeFrequency(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(MERGE_TO_MAIN_FREQUENCY_PER_WEEK, from, to, null);
    }

    @Operation(summary = "Knowledge Silo Score for the current user")
    @GetMapping("/knowledge-silo")
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

    @Operation(summary = "Merge Without Review Ratio for the current user")
    @GetMapping("/merge-without-review")
    public MetricAggregateDto getMergeWithoutReview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(MERGE_WITHOUT_REVIEW_RATIO, from, to, repoId);
    }

    // =========================================================================
    // Team endpoints (MANAGER / ADMIN only)
    // =========================================================================

    @Operation(summary = "Calculate and persist team-scoped daily metrics for a date range (manager/admin only)")
    @PostMapping("/teams/{teamId}/calculate")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public void calculateForTeam(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User user = checkHelper.currentUser();
        metricsService.calculateForTeam(teamId, user.getId(), from, to);
    }

    /**
     * Per-member daily commits series + one "team" aggregate row.
     * Managers and admins receive the full per-member breakdown; developers who are team members
     * receive only the aggregate row (userId=null) — per-member rows are never sent to DEVELOPER role.
     * Optional repoId scopes results to snapshots for that specific repository.
     */
    @Operation(summary = "Per-member + aggregate Daily Commits Count series for a team")
    @GetMapping("/teams/{teamId}/daily-commits")
    @PreAuthorize("@teamAccessGuard.canRead(#teamId, authentication)")
    public List<TeamMetricPointDto> getTeamDailyCommits(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        List<TeamMetricPointDto> series = buildTeamDailySeries(teamId, DAILY_COMMITS_COUNT, from, to, repoId);
        if (SecurityUtils.getCurrentUserRole() == Role.DEVELOPER) {
            return series.stream().filter(p -> p.userId() == null).toList();
        }
        return series;
    }

    /**
     * Aggregate-only team PR merged series. Accessible to team members (not just managers).
     * Developers receive only the aggregate row; managers/admins receive per-member breakdown.
     * Optional repoId scopes results to snapshots for that specific repository.
     */
    @Operation(summary = "Per-member + aggregate Daily PRs Merged series for a team")
    @GetMapping("/teams/{teamId}/daily-pr-merged")
    @PreAuthorize("@teamAccessGuard.canRead(#teamId, authentication)")
    public List<TeamMetricPointDto> getTeamDailyPrMerged(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        List<TeamMetricPointDto> series = buildTeamDailySeries(teamId, DAILY_PR_MERGED, from, to, repoId);
        if (SecurityUtils.getCurrentUserRole() == Role.DEVELOPER) {
            return series.stream().filter(p -> p.userId() == null).toList();
        }
        return series;
    }

    /**
     * Aggregate-only team issues closed series. Accessible to team members (not just managers).
     * Developers receive only the aggregate row; managers/admins receive per-member breakdown.
     * Optional repoId scopes results to snapshots for that specific repository.
     */
    @Operation(summary = "Per-member + aggregate Daily Issues Closed series for a team")
    @GetMapping("/teams/{teamId}/daily-issues-closed")
    @PreAuthorize("@teamAccessGuard.canRead(#teamId, authentication)")
    public List<TeamMetricPointDto> getTeamDailyIssuesClosed(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        List<TeamMetricPointDto> series = buildTeamDailySeries(teamId, DAILY_ISSUES_CLOSED, from, to, repoId);
        if (SecurityUtils.getCurrentUserRole() == Role.DEVELOPER) {
            return series.stream().filter(p -> p.userId() == null).toList();
        }
        return series;
    }

    /** One MemberSummaryDto per member — all metric types summed over the window. */
    @Operation(summary = "Per-member metric totals for a team over a date range (manager/admin only)")
    @GetMapping("/teams/{teamId}/summary")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<MemberSummaryDto> getTeamSummary(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Team team = requireTeam(teamId);
        List<User> members = new ArrayList<>(team.getMembers());
        if (members.isEmpty()) return List.of();

        List<Long> memberIds = members.stream().map(User::getId).toList();
        Map<Long, Map<MetricType, Double>> byUser = new HashMap<>();
        for (User m : members) byUser.put(m.getId(), new EnumMap<>(MetricType.class));

        for (MetricType type : MetricType.values()) {
            // Team-scoped snapshots: saved with team=teamId by calculateForTeam,
            // which filters every metric by the member's authorEmail / githubLogin.
            // This gives correct per-member attribution even on shared repos.
            metricSnapshotService
                    .getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(memberIds, teamId, type, from, to)
                    .forEach(s -> byUser.get(s.getUser().getId()).merge(type, s.getValue(), Double::sum));
        }

        return members.stream()
                .map(m -> new MemberSummaryDto(
                        m.getId(), m.getUsername(), byUser.get(m.getId()),
                        m.getAvatarData() != null, m.getAvatarPreset(),
                        m.getLastActiveAt(), m.getEmail()))
                .toList();
    }

    /**
     * Manager view of a specific team member's metrics within this team's scope.
     * Uses team-scoped snapshots so attribution is filtered by author identity.
     */
    @Operation(summary = "Metric totals for one team member within the team's scope (manager/admin only)")
    @GetMapping("/teams/{teamId}/members/{memberId}/summary")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public MemberSummaryDto getMemberSummary(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Team team = requireTeam(teamId);
        User member = userService.getById(memberId);

        Map<MetricType, Double> metrics = new EnumMap<>(MetricType.class);
        for (MetricType type : MetricType.values()) {
            metricSnapshotService
                    .getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(member, team, type, from, to)
                    .forEach(s -> metrics.merge(type, s.getValue(), Double::sum));
        }

        return new MemberSummaryDto(
                member.getId(), member.getUsername(), metrics,
                member.getAvatarData() != null, member.getAvatarPreset(),
                member.getLastActiveAt(), member.getEmail());
    }

    @Operation(summary = "Daily Commits Count series for one team member (manager/admin only)")
    @GetMapping("/teams/{teamId}/members/{memberId}/daily-commits")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<MetricPointDto> getMemberDailyCommits(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getMemberDailySeries(teamId, memberId, DAILY_COMMITS_COUNT, from, to);
    }

    @Operation(summary = "Daily PRs Created series for one team member (manager/admin only)")
    @GetMapping("/teams/{teamId}/members/{memberId}/daily-pr-created")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<MetricPointDto> getMemberDailyPrCreated(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getMemberDailySeries(teamId, memberId, DAILY_PR_CREATED, from, to);
    }

    @Operation(summary = "Daily Churn Ratio series for one team member (manager/admin only)")
    @GetMapping("/teams/{teamId}/members/{memberId}/daily-churn")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<MetricPointDto> getMemberDailyChurn(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getMemberDailySeries(teamId, memberId, DAILY_CHURN_RATIO, from, to);
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
     * Returns the latest date for which personal metrics have been computed.
     * Used by the dashboard to show a "metrics current through {date}" freshness indicator.
     */
    @Operation(summary = "Latest date through which the current user's personal metrics have been computed")
    @GetMapping("/freshness")
    public ResponseEntity<Map<String, String>> getFreshness() {
        User user = checkHelper.currentUser();
        return metricSnapshotService.findMaxPersonalDate(user.getId())
                .map(date -> ResponseEntity.ok(Map.of("metricsComputedThrough", date.toString())))
                .orElse(ResponseEntity.ok(Map.of()));
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

        GitRepositoryEntity repo = repoService.getById(repoId);
        return metricSnapshotService
                .getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(user, type, repo, from, to)
                .stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    private MetricAggregateDto getPersonalLeadTimeAggregate(MetricType type, LocalDate from, LocalDate to, Long repoId) {
        User user = checkHelper.currentUser();
        List<MetricSnapshot> list;

        if (repoId == null) {
            list = metricSnapshotService
                    .getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(user, type, from, to);
        } else {
            GitRepositoryEntity repo = repoService.getById(repoId);
            list = metricSnapshotService
                    .getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo(user, type, repo, from, to);
        }

        if (list.isEmpty()) return new MetricAggregateDto(type, 0.0, null, null);

        if (repoId == null && list.size() > 1) {
            // Cross-repo: compute median of per-repo values rather than picking an arbitrary snapshot.
            List<Double> sorted = list.stream().mapToDouble(MetricSnapshot::getValue).sorted().boxed().toList();
            int n = sorted.size();
            double median = (n % 2 == 0)
                    ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
                    : sorted.get(n / 2);
            return new MetricAggregateDto(type, median, from, to);
        }

        MetricSnapshot last = list.stream().max(Comparator.comparing(MetricSnapshot::getDate)).orElseThrow();
        return new MetricAggregateDto(last.getMetricType(), last.getValue(), last.getPeriodFrom(), last.getPeriodTo());
    }

    private List<TeamMetricPointDto> buildTeamDailySeries(Long teamId, MetricType type,
                                                          LocalDate from, LocalDate to,
                                                          Long repoId) {
        Team team = requireTeam(teamId);
        List<Long> memberIds = team.getMembers().stream().map(User::getId).toList();
        if (memberIds.isEmpty()) return List.of();

        // Team-scoped snapshots: each member's commits are attributed by authorEmail,
        // so data from shared repos is correctly split per developer.
        List<MetricSnapshot> snapshots;
        if (repoId != null) {
            GitRepositoryEntity repo = repoService.getById(repoId);
            snapshots = metricSnapshotService
                    .getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndRepositoryAndDateBetween(
                            memberIds, teamId, type, repo, from, to);
        } else {
            snapshots = metricSnapshotService
                    .getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(memberIds, teamId, type, from, to);
        }

        // Group by (userId, date) to collapse per-repo rows into per-member daily totals
        Map<Long, Map<LocalDate, Double>> perUserPerDay = new HashMap<>();
        Map<Long, String> usernames = new HashMap<>();
        for (MetricSnapshot s : snapshots) {
            Long uid = s.getUser().getId();
            perUserPerDay.computeIfAbsent(uid, k -> new HashMap<>())
                         .merge(s.getDate(), s.getValue(), Double::sum);
            usernames.put(uid, s.getUser().getUsername());
        }

        List<TeamMetricPointDto> result = new ArrayList<>();
        Map<LocalDate, Double> aggregateByDay = new TreeMap<>();

        perUserPerDay.forEach((uid, dayMap) ->
                dayMap.forEach((date, total) -> {
                    result.add(new TeamMetricPointDto(date, total, type.name(), uid, usernames.get(uid)));
                    aggregateByDay.merge(date, total, Double::sum);
                }));

        result.sort(Comparator.comparing(TeamMetricPointDto::date));
        aggregateByDay.forEach((date, total) ->
                result.add(new TeamMetricPointDto(date, total, type.name(), null, "team")));

        return result;
    }

    private List<MetricPointDto> getMemberDailySeries(Long teamId, Long memberId, MetricType type,
                                                      LocalDate from, LocalDate to) {
        Team team = requireTeam(teamId);
        User member = userService.getById(memberId);

        Map<LocalDate, Double> sumByDate = new TreeMap<>();
        metricSnapshotService
                .getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(member, team, type, from, to)
                .forEach(s -> sumByDate.merge(s.getDate(), s.getValue(), Double::sum));
        return sumByDate.entrySet().stream()
                .map(e -> new MetricPointDto(e.getKey(), e.getValue(), type.name(), null, null))
                .toList();
    }

    private Team requireTeam(Long teamId) {
        return teamService.getById(teamId);
    }
}
