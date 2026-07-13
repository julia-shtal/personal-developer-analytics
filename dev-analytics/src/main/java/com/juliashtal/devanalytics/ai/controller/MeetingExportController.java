package com.juliashtal.devanalytics.ai.controller;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MeetingExportService;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.metrics.model.MemberSummaryDto;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * REST controller for 1:1 meeting exports. Mounted at /api/teams — renders member metrics as a markdown prep document.
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class MeetingExportController {

    private final MetricsAiService metricsAiService;
    private final MeetingExportService meetingExportService;
    private final MetricsAnomalyService metricsAnomalyService;
    private final MetricSnapshotService metricSnapshotService;
    private final TeamService teamService;
    private final UserService userService;
    private final CheckHelper checkHelper;

    @Operation(summary = "Export 1:1 meeting prep document for a team member as markdown (manager/admin only)")
    @GetMapping(value = "/{teamId}/members/{memberId}/export", produces = "text/markdown")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<String> exportMeetingPrep(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        User requestingUser = checkHelper.currentUser();
        Team team = teamService.getById(teamId);
        User member = userService.getById(memberId);

        // Metrics for a team member are stored with team_id set (CLAUDE.md: metric scope rule).
        Map<MetricType, Double> metrics = new EnumMap<>(MetricType.class);
        for (MetricType type : MetricType.values()) {
            metricSnapshotService
                    .getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(member, team, type, from, to)
                    .forEach(s -> metrics.merge(type, s.getValue(), Double::sum));
        }
        MemberSummaryDto summary = new MemberSummaryDto(
                member.getId(), member.getUsername(), metrics,
                member.getAvatarData() != null, member.getAvatarPreset(),
                member.getLastActiveAt(), member.getEmail());

        MetricsSummaryDto aiSummary = metricsAiService.generateMemberSummary(
                requestingUser, teamId, memberId, from, to);

        Map<MetricType, Boolean> anomalies = metricsAnomalyService.computeAnomalies(member, from, to);

        String markdown = meetingExportService.buildMarkdown(
                member, summary, aiSummary, anomalies, from, to, aiSummary.getModelName());

        String filename = String.format("1on1-%s-%s-%s.md",
                member.getUsername().replaceAll("[^a-zA-Z0-9_-]", "_"), from, to);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(markdown);
    }
}
