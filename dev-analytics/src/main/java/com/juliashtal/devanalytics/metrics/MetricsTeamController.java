package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.metrics.model.*;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

@RestController
@RequestMapping("/api/metrics/teams")
@RequiredArgsConstructor
public class MetricsTeamController {

    private final MetricSnapshotService metricSnapshotService;
    private final MetricsService metricsService;
    private final RepoService repoService;
    private final TeamService teamService;
    private final UserService userService;
    private final CheckHelper checkHelper;

    @Operation(summary = "Calculate and persist team-scoped daily metrics for a date range (manager/admin only)")
    @PostMapping("/{teamId}/calculate")
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
     */
    @Operation(summary = "Per-member + aggregate Daily Commits Count series for a team")
    @GetMapping("/{teamId}/daily-commits-count")
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
     * Aggregate-only team PR merged series. Developers receive only the aggregate row;
     * managers/admins receive per-member breakdown.
     */
    @Operation(summary = "Per-member + aggregate Daily PRs Merged series for a team")
    @GetMapping("/{teamId}/daily-pr-merged")
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
     * Per-member + aggregate team issues closed series. Developers receive only the aggregate row;
     * managers/admins receive per-member breakdown.
     */
    @Operation(summary = "Per-member + aggregate Daily Issues Closed series for a team")
    @GetMapping("/{teamId}/daily-issues-closed")
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
    @GetMapping("/{teamId}/summary")
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
    @GetMapping("/{teamId}/members/{memberId}/summary")
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
    @GetMapping("/{teamId}/members/{memberId}/daily-commits-count")
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
    @GetMapping("/{teamId}/members/{memberId}/daily-pr-created")
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
    @GetMapping("/{teamId}/members/{memberId}/daily-churn-ratio")
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

    private List<TeamMetricPointDto> buildTeamDailySeries(Long teamId, MetricType type,
                                                          LocalDate from, LocalDate to,
                                                          Long repoId) {
        Team team = requireTeam(teamId);
        List<Long> memberIds = team.getMembers().stream().map(User::getId).toList();
        if (memberIds.isEmpty()) return List.of();

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
