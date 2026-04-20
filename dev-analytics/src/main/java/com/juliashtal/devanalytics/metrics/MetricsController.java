package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.metrics.model.*;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

@RestController
@RequestMapping("/api/metrics")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricSnapshotRepository metricSnapshotRepository;
    private final MetricsService metricsService;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
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
        return metricSnapshotRepository
                .findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, FOCUS_RATIO_DAYS_TASKS, from, to)
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
        var list = metricSnapshotRepository
                .findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, FOCUS_RATIO_DAYS_TASKS, from, to);

        if (list.isEmpty()) return new MetricAggregateDto(FOCUS_RATIO_DAYS_TASKS, 0.0, null, null);

        double avg = list.stream().mapToDouble(MetricSnapshot::getValue).average().orElse(0.0);
        return new MetricAggregateDto(FOCUS_RATIO_DAYS_TASKS, avg, null, null);
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
            metricSnapshotRepository
                    .findByUserIdsAndTeamIdAndMetricTypeAndDateBetween(memberIds, teamId, type, from, to)
                    .forEach(s -> byUser.get(s.getUser().getId()).merge(type, s.getValue(), Double::sum));
        }

        return members.stream()
                .map(m -> new MemberSummaryDto(m.getId(), m.getUsername(), byUser.get(m.getId())))
                .toList();
    }

    /**
     * Manager view of a specific team member's metrics within this team's scope.
     * Useful when a user belongs to multiple teams — this shows only their
     * contribution to teamId, not other teams.
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
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + memberId));

        Map<MetricType, Double> metrics = new EnumMap<>(MetricType.class);
        for (MetricType type : MetricType.values()) {
            metricSnapshotRepository
                    .findByUserAndTeamAndMetricTypeAndDateBetween(member, team, type, from, to)
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
            metricSnapshotRepository
                    .findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, type, from, to)
                    .forEach(s -> sumByDate.merge(s.getDate(), s.getValue(), Double::sum));
            return sumByDate.entrySet().stream()
                    .map(e -> new MetricPointDto(e.getKey(), e.getValue(), type.name(), null, null))
                    .toList();
        }

        GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
        return metricSnapshotRepository
                .findByUserAndTeamIsNullAndMetricTypeAndRepositoryAndDateBetween(user, type, repo, from, to)
                .stream()
                .sorted(Comparator.comparing(MetricSnapshot::getDate))
                .map(MetricPointDto::fromEntity)
                .toList();
    }

    private MetricAggregateDto getPersonalLeadTimeAggregate(MetricType type, LocalDate from, LocalDate to, Long repoId) {
        User user = checkHelper.currentUser();
        List<MetricSnapshot> list;

        if (repoId == null) {
            list = metricSnapshotRepository
                    .findByUserAndTeamIsNullAndMetricTypeAndDateBetween(user, type, from, to);
        } else {
            GitRepositoryEntity repo = gitRepoRepository.getReferenceById(repoId);
            list = metricSnapshotRepository
                    .findByUserAndTeamIsNullAndMetricTypeAndRepositoryAndDateBetween(user, type, repo, from, to);
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

        List<MetricSnapshot> snapshots = metricSnapshotRepository
                .findByUserIdsAndTeamIdAndMetricTypeAndDateBetween(memberIds, teamId, type, from, to);

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
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + memberId));

        Map<LocalDate, Double> sumByDate = new TreeMap<>();
        metricSnapshotRepository
                .findByUserAndTeamAndMetricTypeAndDateBetween(member, team, type, from, to)
                .forEach(s -> sumByDate.merge(s.getDate(), s.getValue(), Double::sum));
        return sumByDate.entrySet().stream()
                .map(e -> new MetricPointDto(e.getKey(), e.getValue(), type.name(), null, null))
                .toList();
    }

    private Team requireTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));
    }
}
