package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.service.RepoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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

    @GetMapping
    public List<RepoDto> listAccessible(
            @RequestParam(required = false) Long dataSourceId) {
        return repoService.listAccessible(dataSourceId);
    }

    @PostMapping("/{repoId}/subscribe")
    public ResponseEntity<Void> subscribe(@PathVariable Long repoId) {
        repoService.subscribe(repoId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{repoId}/subscribe")
    public ResponseEntity<Void> unsubscribe(@PathVariable Long repoId) {
        repoService.unsubscribe(repoId);
        return ResponseEntity.noContent().build();
    }
}
