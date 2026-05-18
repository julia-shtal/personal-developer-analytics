package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.dto.AttachRepoRequest;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.github.model.dto.DiscoveredRepoDto;
import com.juliashtal.devanalytics.github.model.dto.DiscoveryResult;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/datasources/{id}/repos")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "Datasource Repos")
public class DataSourceRepoController {

    private final DataSourceService dataSourceService;
    private final GitHubRepositoryService gitHubRepositoryService;

    @GetMapping
    @Operation(summary = "List repositories attached to a datasource")
    public List<RepoDto> list(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return dataSourceService.listReposForDataSource(userId, id);
    }

    @PostMapping
    @Operation(summary = "Attach a repository to a GITHUB datasource (idempotent)")
    public ResponseEntity<RepoDto> attach(
            @PathVariable Long id,
            @RequestBody @Valid AttachRepoRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<RepoDto> before = dataSourceService.listReposForDataSource(userId, id);
        boolean alreadyPresent = before.stream()
                .anyMatch(r -> req.repoFullName().equalsIgnoreCase(r.repoFullName()));

        RepoDto dto = dataSourceService.attachRepo(userId, id, req.repoFullName(), req.collectIssues());

        if (alreadyPresent) {
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity
                .created(URI.create("/api/datasources/" + id + "/repos/" + dto.id()))
                .body(dto);
    }

    @DeleteMapping("/{repoId}")
    @Operation(summary = "Detach a repository from a datasource (fails with 409 if subscriptions exist)")
    public ResponseEntity<Void> detach(@PathVariable Long id, @PathVariable Long repoId) {
        Long userId = SecurityUtils.getCurrentUserId();
        dataSourceService.detachRepo(userId, id, repoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/discover-repos")
    @Operation(summary = "List GitHub repositories visible to the stored token, annotated with alreadyAttached. Cached 60 s.")
    public ResponseEntity<List<DiscoveredRepoDto>> discoverRepos(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        DiscoveryResult result = gitHubRepositoryService.discoverRepos(userId, id);
        return ResponseEntity.ok()
                .header("X-Discovery-Truncated", String.valueOf(result.truncated()))
                .body(result.repos());
    }
}
