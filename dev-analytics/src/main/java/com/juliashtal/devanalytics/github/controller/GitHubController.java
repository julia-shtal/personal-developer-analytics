package com.juliashtal.devanalytics.github.controller;

import com.juliashtal.devanalytics.git.model.dto.GitRepositoryDto;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.github.model.dto.RegisterGitHubRepoRequest;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for GitHub repositories. Mounted at /api/github — registration and commit collection.
 */
@RestController
@RequestMapping("/api/github")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class GitHubController {

    private final GitHubRepositoryService gitHubRepositoryService;
    private final GitHubCollector gitHubCollector;

    @Operation(summary = "Register a GitHub repository under a data source, reusing an existing entity if already registered")
    @PostMapping("/repos")
    public ResponseEntity<GitRepositoryDto> registerRepo(@RequestBody RegisterGitHubRepoRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        GitRepositoryEntity repo = gitHubRepositoryService.registerGitHubRepo(
                userId,
                request.getDataSourceId(),
                request.getFullName()
        );
        return ResponseEntity.ok(GitRepositoryDto.fromEntity(repo));
    }

    @Operation(summary = "Synchronously collect new commits for a GitHub repository")
    @PostMapping("/repos/{repoId}/collect")
    public ResponseEntity<String> collect(@PathVariable Long repoId) {
        int saved = gitHubCollector.collectForRepository(repoId, null);
        return ResponseEntity.ok("Collected " + saved + " commits from GitHub");
    }
}

