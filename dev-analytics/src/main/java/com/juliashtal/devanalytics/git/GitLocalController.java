package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.git.model.dto.GitCommitDto;
import com.juliashtal.devanalytics.git.model.dto.GitRepositoryDto;
import com.juliashtal.devanalytics.git.model.dto.RegisterLocalRepoRequest;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import com.juliashtal.devanalytics.git.service.GitRepositoryService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/git/local")
@PreAuthorize("isAuthenticated()")
public class GitLocalController {

    private final GitRepositoryService gitRepositoryService;
    private final GitLocalCollector gitLocalCollector;

    public GitLocalController(GitRepositoryService gitRepositoryService,
                              GitLocalCollector gitLocalCollector) {
        this.gitRepositoryService = gitRepositoryService;
        this.gitLocalCollector = gitLocalCollector;
    }

    // 1. Local repo registration
    @PostMapping("/repos")
    public ResponseEntity<GitRepositoryDto> registerLocalRepo(
            @RequestBody @Valid RegisterLocalRepoRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        var repo = gitRepositoryService.registerLocalRepo(userId, request);
        return ResponseEntity.ok(GitRepositoryDto.fromEntity(repo));
    }

    // 2. List of all local repositories for the current user
    @GetMapping("/repos")
    public List<GitRepositoryDto> listRepos() {
        Long userId = SecurityUtils.getCurrentUserId();
        return gitRepositoryService.listReposForUser(userId).stream()
                .map(GitRepositoryDto::fromEntity)
                .toList();
    }

    // 3. Details of one repo
    @GetMapping("/repos/{repoId}")
    public GitRepositoryDto getRepo(@PathVariable Long repoId) {
        Long userId = SecurityUtils.getCurrentUserId();
        var repo = gitRepositoryService.getRepoForUser(userId, repoId);
        return GitRepositoryDto.fromEntity(repo);
    }

    // 4. Repo commits with pagination
    @GetMapping("/repos/{repoId}/commits")
    public Page<GitCommitDto> listCommits(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        var commitsPage = gitRepositoryService.listCommitsForRepo(userId, repoId, PageRequest.of(page, size));
        return commitsPage.map(GitCommitDto::fromEntity);
    }

    // 5. Manual start of the collector
    @PostMapping("/repos/{repoId}/collect")
    public ResponseEntity<String> collect(@PathVariable Long repoId) {
        long saved = gitLocalCollector.collectForRepository(repoId);
        return ResponseEntity.ok("Collected " + saved + " commits");
    }
}

