package com.juliashtal.devanalytics.gitlab.controller;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.dto.GitRepositoryDto;
import com.juliashtal.devanalytics.gitlab.model.dto.RegisterGitLabRepoRequest;
import com.juliashtal.devanalytics.gitlab.service.GitLabCollector;
import com.juliashtal.devanalytics.gitlab.service.GitLabRepositoryService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gitlab")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "GitLab")
public class GitLabController {

    private final GitLabRepositoryService gitLabRepositoryService;
    private final GitLabCollector gitLabCollector;

    @PostMapping("/repos")
    @Operation(summary = "Register a GitLab repository under a datasource")
    public ResponseEntity<GitRepositoryDto> registerRepo(@RequestBody RegisterGitLabRepoRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        GitRepositoryEntity repo = gitLabRepositoryService.registerGitLabRepo(
                userId,
                request.getDataSourceId(),
                request.getFullName()
        );
        return ResponseEntity.ok(GitRepositoryDto.fromEntity(repo));
    }

    @PostMapping("/repos/{repoId}/collect")
    @Operation(summary = "Trigger commit collection for a GitLab repository")
    public ResponseEntity<String> collect(@PathVariable Long repoId) {
        int saved = gitLabCollector.collectForRepository(repoId, null);
        return ResponseEntity.ok("Collected " + saved + " commits from GitLab");
    }
}
