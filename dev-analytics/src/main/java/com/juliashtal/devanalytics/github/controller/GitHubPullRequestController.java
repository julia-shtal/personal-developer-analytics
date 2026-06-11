package com.juliashtal.devanalytics.github.controller;

import com.juliashtal.devanalytics.github.model.dto.GitHubPullRequestDto;

import com.juliashtal.devanalytics.github.service.GitHubPrCollector;
import com.juliashtal.devanalytics.github.service.GitHubPullRequestCollector;
import io.swagger.v3.oas.annotations.Operation;
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

    private final GitHubPrCollector prCollector;
    private final GitHubPullRequestCollector prQueryService;

    @Operation(summary = "Synchronously collect new and updated pull requests for a GitHub repository")
    @PostMapping("/repos/{repoId}/pull-requests/collect")
    public ResponseEntity<String> collectPrs(@PathVariable Long repoId) {
        int processed = prCollector.collectForRepository(repoId, null);
        return ResponseEntity.ok("Processed " + processed + " pull requests");
    }

    @Operation(summary = "List pull requests for a repository, paginated")
    @GetMapping("/repos/{repoId}/pull-requests")
    public Page<GitHubPullRequestDto> listPrs(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        var prs = prQueryService.listPullRequests(repoId, PageRequest.of(page, size));
        return prs.map(GitHubPullRequestDto::fromEntity);
    }
}

