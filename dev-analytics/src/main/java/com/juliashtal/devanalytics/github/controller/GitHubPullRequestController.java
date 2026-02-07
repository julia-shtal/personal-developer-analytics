package com.juliashtal.devanalytics.github.controller;

import com.juliashtal.devanalytics.github.model.dto.GitHubPullRequestDto;

import com.juliashtal.devanalytics.github.service.GitHubPullRequestCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class GitHubPullRequestController {

    private final GitHubPullRequestCollector prCollector;

    // 1. Start collecting PRs for the repository
    @PostMapping("/repos/{repoId}/pull-requests/collect")
    public ResponseEntity<String> collectPrs(@PathVariable Long repoId) {
        // TODO Long userId = SecurityUtils.getCurrentUserId();
        int processed = prCollector.collectPullRequests(repoId);
        return ResponseEntity.ok("Processed " + processed + " pull requests");
    }

    // 2. Viewing PRs by repo
    @GetMapping("/repos/{repoId}/pull-requests")
    public Page<GitHubPullRequestDto> listPrs(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        var prs = prCollector.listPullRequests(repoId, PageRequest.of(page, size));
        return prs.map(GitHubPullRequestDto::fromEntity);
    }
}

