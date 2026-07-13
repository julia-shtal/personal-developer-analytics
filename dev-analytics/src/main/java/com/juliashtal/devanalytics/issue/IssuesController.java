package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.issue.model.IssueDto;
import com.juliashtal.devanalytics.issue.service.IssueService;
import com.juliashtal.devanalytics.jira.service.JiraCollector;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for issues. Mounted at /api/issues — unified GitHub and Jira issue collection and listing.
 */
@RestController
@RequestMapping("/api/issues")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class IssuesController {

    private final IssueService issueService;
    private final JiraCollector jiraCollector;
    private final JiraProjectService jiraProjectService;
    private final GitHubIssuesCollector gitHubIssuesCollector;
    private final DataSourceService dataSourceService;
    private final RepoService repoService;

    @GetMapping("/count")
    @Operation(summary = "Open/closed issue count for a GitHub repository")
    public Map<String, Long> getIssueCount(@RequestParam Long repoId) {
        return issueService.countByRepository(repoId);
    }

    @GetMapping("/jira/{projectId}/count")
    @Operation(summary = "Open/closed issue count for a tracked Jira project")
    public Map<String, Long> getJiraProjectIssueCount(@PathVariable Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        jiraProjectService.getProjectForUser(projectId, userId);
        return issueService.countByJiraProject(projectId);
    }

    @PostMapping("/jira/{projectId}/collect")
    @Operation(summary = "Trigger Jira issue collection for a tracked project")
    public ResponseEntity<String> collectJira(@PathVariable Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        JiraProjectEntity project = jiraProjectService.getProjectForUser(projectId, userId);
        int count = jiraCollector.collectIssues(project);
        return ResponseEntity.ok("Collected/updated " + count + " Jira issues");
    }

    @PostMapping("/github/{dataSourceId}/repos/{owner}/{repo}/collect")
    @Operation(summary = "Trigger GitHub issue collection for a repository")
    public ResponseEntity<String> collectGitHub(
            @PathVariable Long dataSourceId,
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig cfg = getUserDataSource(userId, dataSourceId, DataSourceType.GITHUB);
        String fullName = owner + "/" + repo;
        int count = gitHubIssuesCollector.collectIssuesForRepo(cfg, fullName);
        return ResponseEntity.ok("Collected/updated " + count + " GitHub issues");
    }

    @GetMapping("/jira/{projectId}")
    @Operation(summary = "List issues for a tracked Jira project")
    public Page<IssueDto> listJiraIssues(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        JiraProjectEntity project = jiraProjectService.getProjectForUser(projectId, userId);
        return issueService.getByJiraProject(project, PageRequest.of(page, size))
                .map(IssueDto::fromEntity);
    }

    @GetMapping("/github/{repoId}")
    @Operation(summary = "List issues for a GitHub repository")
    public Page<IssueDto> listGitHubIssues(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        GitRepositoryEntity repo = repoService.getAccessibleRepo(userId, repoId);

        return issueService.getByRepository(repo, PageRequest.of(page, size))
                .map(IssueDto::fromEntity);
    }

    private DataSourceConfig getUserDataSource(Long userId, Long dataSourceId, DataSourceType expectedType) {
        DataSourceConfig cfg = dataSourceService.getDataSource(dataSourceId);
        if (!cfg.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("DataSource does not belong to current user");
        }
        if (cfg.getType() != expectedType) {
            throw new IllegalArgumentException("DataSource type must be " + expectedType);
        }
        return cfg;
    }
}
