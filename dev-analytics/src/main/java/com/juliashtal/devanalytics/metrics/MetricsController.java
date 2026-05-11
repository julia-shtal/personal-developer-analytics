package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.metrics.model.*;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

@RestController
@RequestMapping("/api/metrics")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricSnapshotService metricSnapshotService;
    private final MetricsService metricsService;
    private final RepoService repoService;
    private final TeamService teamService;
    private final UserService userService;
    private final CheckHelper checkHelper;

    // =========================================================================
    // Personal endpoints — team IS NULL snapshots only
    // =========================================================================

    @PostMapping("/calculate")
    public void calculate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User user = checkHelper.currentUser();
        metricsService.calculateDailyMetrics(user.getId(), from, to);
    }

    @GetMapping("/daily-commits")
    public List<MetricPointDto> getDailyCommits(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_COMMITS_COUNT, from, to, repoId);
    }

    @GetMapping("/daily-pr-created")
    public List<MetricPointDto> getDailyPrCreated(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_PR_CREATED, from, to, repoId);
    }

    @GetMapping("/daily-pr-merged")
    public List<MetricPointDto> getDailyPrMerged(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_PR_MERGED, from, to, repoId);
    }

    @GetMapping("/daily-issues-closed")
    public List<MetricPointDto> getDailyIssuesClosed(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_ISSUES_CLOSED, from, to, repoId);
    }

    @GetMapping("/daily-issues-created")
    public List<MetricPointDto> getDailyIssuesCreated(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_ISSUES_CREATED, from, to, repoId);
    }

    @GetMapping("/daily-churn")
    public List<MetricPointDto> getDailyChurn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalDailySeries(DAILY_CHURN_RATIO, from, to, repoId);
    }

    @GetMapping("/pr-lead-time")
    public MetricAggregateDto getPrLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(PR_LEAD_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @GetMapping("/pr-first-commit-lead-time")
    public MetricAggregateDto getPrFirstCommitLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @GetMapping("/review-response-time")
    public MetricAggregateDto getReviewResponseTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(REVIEW_RESPONSE_TIME_HOURS_MEDIAN, from, to, repoId);
    }

    @GetMapping("/issue-lead-time")
    public MetricAggregateDto getIssueLeadTimeMedian(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(ISSUE_LEAD_TIME_HOURS_MEDIAN, from, to, repoId);
    }

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
    // Ticket 5 — Wellness + Quality metric endpoints (personal, aggregate)
    // =========================================================================

    @GetMapping("/after-hours-ratio")
    public MetricAggregateDto getAfterHoursRatio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(AFTER_HOURS_COMMIT_RATIO, from, to, null);
    }

    @GetMapping("/refactor-ratio")
    public MetricAggregateDto getRefactorRatio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(REFACTOR_RATIO, from, to, null);
    }

    @GetMapping("/deep-work-streak")
    public MetricAggregateDto getDeepWorkStreak(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(DEEP_WORK_STREAK_DAYS, from, to, null);
    }

    @GetMapping("/merge-to-main-frequency")
    public MetricAggregateDto getMergeFrequency(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return getPersonalLeadTimeAggregate(MERGE_TO_MAIN_FREQUENCY_PER_WEEK, from, to, null);
    }

    @GetMapping("/knowledge-silo")
    public MetricAggregateDto getKnowledgeSilo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(KNOWLEDGE_SILO_SCORE, from, to, repoId);
    }

    @GetMapping("/pr-size-complexity")
    public MetricAggregateDto getPrSizeComplexity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId
    ) {
        return getPersonalLeadTimeAggregate(PR_SIZE_COMPLEXITY_SCORE, from, to, repoId);
    }

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

    /** Per-member daily commits series + one "team" aggregate row. */
    @GetMapping("/teams/{teamId}/daily-commits")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<TeamMetricPointDto> getTeamDailyCommits(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return buildTeamDailySeries(teamId, DAILY_COMMITS_COUNT, from, to);
    }

    /** One MemberSummaryDto per member — all metric types summed over the window. */
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
                .map(m -> new MemberSummaryDto(m.getId(), m.getUsername(), byUser.get(m.getId())))
                .toList();
    }

    /**
     * Manager view of a specific team member's metrics within this team's scope.
     * Uses team-scoped snapshots so attribution is filtered by author identity.
     */
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

        return new MemberSummaryDto(member.getId(), member.getUsername(), metrics);
    }

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
    // Private helpers
    // =========================================================================

    private List<MetricPointDto> getPersonalDailySeries(MetricType type, LocalDate from, LocalDate to, Long repoId) {
        User user = checkHelper.currentUser();

        if (repoId == null) {
            Map<LocalDate, Double> sumByDate = new TreeMap<>();
            metricSnapshotService
                    .getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, type, from, to)
                    .forEach(s -> sumByDate.merge(s.getDate(), s.getValue(), Double::sum));
            return sumByDate.entrySet().stream()
                    .map(e -> new MetricPointDto(e.getKey(), e.getValue(), type.name(), null, null))
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

        MetricSnapshot last = list.stream().max(Comparator.comparing(MetricSnapshot::getDate)).orElseThrow();
        return new MetricAggregateDto(last.getMetricType(), last.getValue(), last.getPeriodFrom(), last.getPeriodTo());
    }

    private List<TeamMetricPointDto> buildTeamDailySeries(Long teamId, MetricType type,
                                                          LocalDate from, LocalDate to) {
        Team team = requireTeam(teamId);
        List<Long> memberIds = team.getMembers().stream().map(User::getId).toList();
        if (memberIds.isEmpty()) return List.of();

        // Team-scoped snapshots: each member's commits are attributed by authorEmail,
        // so data from shared repos is correctly split per developer.
        List<MetricSnapshot> snapshots = metricSnapshotService
                .getMetricSnapshotsByUserIdsAndTeamIdAndMetricTypeAndDateBetween(memberIds, teamId, type, from, to);

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
