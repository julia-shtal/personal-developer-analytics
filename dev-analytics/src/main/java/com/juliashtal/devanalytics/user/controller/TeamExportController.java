package com.juliashtal.devanalytics.user.controller;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/teams")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
@RequiredArgsConstructor
public class TeamExportController {

    private final TeamService teamService;
    private final MetricSnapshotService metricSnapshotService;
    private final UserService userService;

    @Operation(summary = "Export team metrics as CSV. Returns username,metric,value,unit,period_from,period_to rows.")
    @GetMapping("/{teamId}/export")
    public ResponseEntity<String> exportTeamCsv(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Team team = teamService.getById(teamId);

        User caller = userService.getById(currentUserId);
        if (caller.getRole() != Role.ADMIN && !team.getManager().getId().equals(currentUserId)) {
            throw new ForbiddenException("Only the team manager or an admin can export team data");
        }

        String safeName = team.getName().replaceAll("[^a-zA-Z0-9\\-]", "_");
        String filename = String.format("team-%s-%s-%s.csv", safeName, from, to);

        StringBuilder csv = new StringBuilder("username,metric,value,unit,period_from,period_to\n");
        for (User member : team.getMembers()) {
            for (MetricType type : MetricType.values()) {
                List<MetricSnapshot> snapshots = metricSnapshotService
                        .getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(
                                member, team, type, from, to);
                if (snapshots.isEmpty()) continue;

                double total = snapshots.stream().mapToDouble(MetricSnapshot::getValue).sum();
                csv.append(String.format("\"%s\",%s,%.4f,%s,%s,%s\n",
                        member.getUsername().replace("\"", "\\\""),
                        type.name(), total, unitFor(type), from, to));
            }
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.toString());
    }

    private static String unitFor(MetricType type) {
        return switch (type) {
            case DAILY_COMMITS_COUNT                           -> "commits";
            case DAILY_COMMITS_AVG_SIZE                        -> "ln/commit";
            case DAILY_PR_CREATED, DAILY_PR_MERGED             -> "prs";
            case DAILY_ISSUES_CREATED, DAILY_ISSUES_CLOSED     -> "issues";
            case DAILY_CHURN_RATIO,
                 AFTER_HOURS_COMMIT_RATIO,
                 KNOWLEDGE_SILO_SCORE,
                 REFACTOR_RATIO,
                 MERGE_WITHOUT_REVIEW_RATIO,
                 FOCUS_RATIO_DAYS_TASKS                        -> "%";
            case PR_LEAD_TIME_HOURS_MEDIAN,
                 PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
                 REVIEW_RESPONSE_TIME_HOURS_MEDIAN,
                 ISSUE_LEAD_TIME_HOURS_MEDIAN,
                 WIP_OPEN_PR_AGE_HOURS_MEDIAN                  -> "h";
            case DEEP_WORK_STREAK_DAYS                         -> "d";
            case PR_SIZE_COMPLEXITY_SCORE                      -> "ln/c";
            case MERGE_TO_MAIN_FREQUENCY_PER_WEEK              -> "/wk";
            case REVIEW_PARTICIPATION_COUNT                    -> "prs";
        };
    }
}
