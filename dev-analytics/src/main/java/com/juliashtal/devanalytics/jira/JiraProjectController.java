package com.juliashtal.devanalytics.jira;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.dto.CreateTrackedJiraProjectRequest;
import com.juliashtal.devanalytics.jira.model.dto.JiraProjectResponseDto;
import com.juliashtal.devanalytics.jira.service.JiraProjectMappingService;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jira-projects")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "Jira Projects")
public class JiraProjectController {

    private final JiraProjectService jiraProjectService;
    private final JiraProjectMappingService jiraProjectMappingService;
    private final DataSourceService dataSourceService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List locally tracked Jira projects for a datasource")
    public List<JiraProjectResponseDto> listTracked(@RequestParam Long dataSourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig ds = getJiraDataSource(userId, dataSourceId);

        User user = userRepository.getReferenceById(userId);
        return jiraProjectService.listTrackedProjects(ds).stream()
                .map(p -> {
                    boolean subscribed = jiraProjectService.isSubscribed(user, p);
                    return JiraProjectResponseDto.from(p, subscribed);
                })
                .toList();
    }

    @GetMapping("/available")
    @Operation(summary = "List projects available from the Jira API for a datasource")
    public List<JiraProjectService.JiraProjectDto> listAvailable(@RequestParam Long dataSourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig ds = getJiraDataSource(userId, dataSourceId);
        return jiraProjectService.listProjects(ds);
    }

    @PostMapping
    @Operation(summary = "Add a Jira project to track under a datasource (datasource owner only)")
    public ResponseEntity<JiraProjectResponseDto> addProject(
            @RequestBody @Valid CreateTrackedJiraProjectRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig ds = getJiraDataSource(userId, req.getDataSourceId());

        JiraProjectEntity project = jiraProjectService.addProject(ds, req.getProjectKey(), req.getProjectName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JiraProjectResponseDto.from(project, false));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a tracked Jira project (datasource owner only)")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        jiraProjectService.deleteProject(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/subscribe")
    @Operation(summary = "Subscribe to a Jira project to see its issues")
    public ResponseEntity<Void> subscribe(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        jiraProjectService.subscribeUser(id, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/subscribe")
    @Operation(summary = "Unsubscribe from a Jira project")
    public ResponseEntity<Void> unsubscribe(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        jiraProjectService.unsubscribeUser(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/repositories")
    @Operation(summary = "List GitHub repos linked to a Jira project")
    public List<RepoDto> listLinkedRepos(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return jiraProjectMappingService.listMappings(id, userId);
    }

    @PostMapping("/{id}/repositories/{repoId}")
    @Operation(summary = "Link a GitHub repo to a Jira project")
    public ResponseEntity<Void> linkRepo(@PathVariable Long id, @PathVariable Long repoId) {
        Long userId = SecurityUtils.getCurrentUserId();
        jiraProjectMappingService.link(userId, id, repoId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/repositories/{repoId}")
    @Operation(summary = "Unlink a GitHub repo from a Jira project")
    public ResponseEntity<Void> unlinkRepo(@PathVariable Long id, @PathVariable Long repoId) {
        Long userId = SecurityUtils.getCurrentUserId();
        jiraProjectMappingService.unlink(userId, id, repoId);
        return ResponseEntity.noContent().build();
    }

    private DataSourceConfig getJiraDataSource(Long userId, Long dataSourceId) {
        DataSourceConfig ds = dataSourceService.getForUser(userId, dataSourceId);
        if (ds.getType() != DataSourceType.JIRA) {
            throw new IllegalArgumentException("DataSource must be of type JIRA");
        }
        return ds;
    }
}
