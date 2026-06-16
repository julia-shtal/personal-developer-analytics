package com.juliashtal.devanalytics.datasource.controller;

import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.jira.model.dto.AttachProjectRequest;
import com.juliashtal.devanalytics.jira.model.dto.DiscoveredProjectDto;
import com.juliashtal.devanalytics.jira.model.dto.JiraProjectResponseDto;
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

@RestController
@RequestMapping("/api/datasources/{id}/projects")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "Datasource Jira Projects")
public class DataSourceJiraProjectController {

    private final JiraProjectService jiraProjectService;

    @GetMapping
    @Operation(summary = "List Jira projects tracked under a datasource")
    public List<JiraProjectResponseDto> list(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return jiraProjectService.listProjectsForDataSource(userId, id);
    }

    @PostMapping
    @Operation(summary = "Attach a Jira project to a datasource (owner only, idempotent). "
            + "Returns 201 when a new canonical row is created under this datasource. "
            + "Returns 200 when the project is canonical under a different datasource — the user "
            + "has been subscribed and the calling datasource deleted if it was empty. "
            + "Callers should check dto.dataSourceId and refresh the datasource list when it "
            + "differs from the requested {id}.")
    public ResponseEntity<JiraProjectResponseDto> attach(
            @PathVariable Long id,
            @RequestBody @Valid AttachProjectRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        JiraProjectResponseDto dto = jiraProjectService.attachProject(userId, id, req.projectKey(), req.projectName());
        if (!id.equals(dto.dataSourceId())) {
            // Cross-DS: project is canonical under dto.dataSourceId(); calling DS may have been deleted.
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity
                .created(URI.create("/api/datasources/" + id + "/projects/" + dto.id()))
                .body(dto);
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Detach a Jira project from a datasource (owner only)")
    public ResponseEntity<Void> detach(@PathVariable Long id, @PathVariable Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        jiraProjectService.detachProject(userId, id, projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/discover-projects")
    @Operation(summary = "List Jira projects visible to the stored token, annotated with alreadyAttached. Cached 60 s.")
    public ResponseEntity<List<DiscoveredProjectDto>> discoverProjects(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<DiscoveredProjectDto> result = jiraProjectService.discoverProjectsFromJira(userId, id);
        return ResponseEntity.ok(result);
    }
}