package com.juliashtal.devanalytics.ai.controller;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
}
