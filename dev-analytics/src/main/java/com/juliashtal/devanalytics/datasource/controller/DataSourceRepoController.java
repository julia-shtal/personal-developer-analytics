package com.juliashtal.devanalytics.datasource.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * REST controller for a datasource's repositories. Mounted at /api/datasources/{id}/repos.
 */
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
    @Operation(summary = "Attach a repository to a GITHUB datasource (idempotent). "
            + "Returns 201 for a new canonical attach. "
            + "Returns 200 when the repo is canonical under a different datasource — the user has been "
            + "subscribed and the calling datasource deleted if it was empty. "
            + "Callers should check dto.dataSourceId and refresh the datasource list when it differs from {id}.")
    public ResponseEntity<RepoDto> attach(
            @PathVariable Long id,
            @RequestBody @Valid AttachRepoRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        RepoDto dto = dataSourceService.attachRepo(userId, id, req.repoFullName(), req.collectIssues());
        if (!id.equals(dto.dataSourceId())) {
            // Cross-DS: repo is canonical under dto.dataSourceId(); calling DS may have been deleted.
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
