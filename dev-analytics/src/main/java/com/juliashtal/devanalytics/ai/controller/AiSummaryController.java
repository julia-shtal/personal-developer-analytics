package com.juliashtal.devanalytics.ai.controller;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MetricSummaryPersistenceService;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST controller for AI-generated metric summaries.
 * Mounted at /api/ai/summary — separate from the existing /api/metrics namespace.
 */
@RestController
@RequestMapping("/api/ai/summary")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AiSummaryController {

    private final MetricsAiService metricsAiService;
    private final MetricSummaryPersistenceService persistenceService;
    private final TeamService teamService;
    private final CheckHelper checkHelper;

    /**
     * Personal summary across all repositories, or filtered to one repo via repoId.
     */
    @GetMapping
    public MetricsSummaryDto getPersonalSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long repoId) {
        User user = checkHelper.currentUser();
        return metricsAiService.generateSummary(user, from, to, repoId);
    }

    /**
     * Summary scoped to a specific repository.
     */
    @GetMapping("/repos/{repoId}")
    public MetricsSummaryDto getRepoSummary(
            @PathVariable Long repoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        User user = checkHelper.currentUser();
        return metricsAiService.generateSummary(user, from, to, repoId);
    }

    /**
     * Team-level summary. Only the team manager or an ADMIN may call this.
     */
    @GetMapping("/teams/{teamId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public MetricsSummaryDto getTeamSummary(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        User user = checkHelper.currentUser();
        return metricsAiService.generateTeamSummary(user, teamId, from, to);
    }

    /**
     * Per-member AI summary scoped to the team context. Only the team manager or an ADMIN may call this.
     */
    @GetMapping("/teams/{teamId}/member/{memberId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public MetricsSummaryDto getMemberSummary(
            @PathVariable Long teamId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        User user = checkHelper.currentUser();
        return metricsAiService.generateMemberSummary(user, teamId, memberId, from, to);
    }

    /**
     * Returns the most recently persisted personal summary for the current user, or 204 if none exists.
     * Used to restore the last summary across server restarts without triggering a new LLM call.
     */
    @GetMapping("/latest")
    public ResponseEntity<MetricsSummaryDto> getLatestPersonalSummary() {
        User user = checkHelper.currentUser();
        return persistenceService.findLatestPersonal(user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Returns the most recently persisted team summary, or 204 if none exists.
     * Only the team manager or an ADMIN may call this.
     */
    @GetMapping("/teams/{teamId}/latest")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<MetricsSummaryDto> getLatestTeamSummary(@PathVariable Long teamId) {
        Team team = teamService.getById(teamId);
        return persistenceService.findLatestTeam(team)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
