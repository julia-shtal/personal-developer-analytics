package com.juliashtal.devanalytics.github.controller;

import com.juliashtal.devanalytics.git.model.dto.GitRepositoryDto;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.github.model.dto.RegisterGitHubRepoRequest;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class GitHubController {

    private final GitHubRepositoryService gitHubRepositoryService;
    private final GitHubCollector gitHubCollector;

    // 1. GitHub repository registration (by fullName “owner/repo”)
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

    // 2. Manually trigger the collector for a specific GitHub repository
    @PostMapping("/repos/{repoId}/collect")
    public ResponseEntity<String> collect(@PathVariable Long repoId) {
        int saved = gitHubCollector.collectForRepository(repoId);
        return ResponseEntity.ok("Collected " + saved + " commits from GitHub");
    }
}

