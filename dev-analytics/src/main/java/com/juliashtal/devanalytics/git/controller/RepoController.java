package com.juliashtal.devanalytics.git.controller;

import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.github.service.AsyncIssuesCollectService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.Map;

/**
 * Unified repo subscription API.
 *
 * GET  /api/repos                  — list all repos the user can see (own + team), with subscribed flag
 * POST /api/repos/{id}/subscribe   — create a UserRepoRegistration (subscribe)
 * DELETE /api/repos/{id}/subscribe — remove a UserRepoRegistration (unsubscribe)
 */
@RestController
@RequestMapping("/api/repos")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;
    private final AsyncIssuesCollectService asyncIssuesCollectService;

    @Operation(summary = "List repos visible to the current user (own + team), with subscribed flag")
    @GetMapping
    public List<RepoDto> listAccessible(
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) Long teamId) {
        return repoService.listAccessible(dataSourceId, teamId);
    }

    @Operation(summary = "Subscribe the current user to a repo's metrics")
    @PostMapping("/{repoId}/subscribe")
    public ResponseEntity<Void> subscribe(@PathVariable Long repoId) {
        repoService.subscribe(repoId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Unsubscribe the current user from a repo's metrics")
    @DeleteMapping("/{repoId}/subscribe")
    public ResponseEntity<Void> unsubscribe(@PathVariable Long repoId) {
        repoService.unsubscribe(repoId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Enable or disable issue collection for a repo, triggering an async backfill when enabled")
    @PatchMapping("/{repoId}/collect-issues")
    public RepoDto setCollectIssues(
            @PathVariable Long repoId,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return repoService.setCollectIssues(repoId, enabled,
                asyncIssuesCollectService::collectIssuesForRepo);
    }
}
