package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.issue.model.IssueDto;
import com.juliashtal.devanalytics.issue.service.IssueService;
import com.juliashtal.devanalytics.jira.JiraCollector;
import com.juliashtal.devanalytics.jira.JiraProjectService;
import com.juliashtal.devanalytics.jira.JiraProjectService.JiraProjectDto;
import com.juliashtal.devanalytics.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/issues")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class IssuesController {

    private final IssueService issueService;
    private final IssueRepository issueRepository;
    private final JiraCollector jiraCollector;
    private final JiraProjectService jiraProjectService;
    private final GitHubIssuesCollector gitHubIssuesCollector;
    private final DataSourceService dataSourceService;

    @GetMapping("/count")
    public Map<String, Long> getIssueCount(@RequestParam Long repoId) {
        long open   = issueRepository.countByRepository_IdAndState(repoId, "open");
        long closed = issueRepository.countByRepository_IdAndState(repoId, "closed");
        return Map.of("open", open, "closed", closed);
    }

    @GetMapping("/jira-projects")
    public List<JiraProjectDto> listJiraProjects(@RequestParam Long dataSourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig cfg = getUserDataSource(userId, dataSourceId, DataSourceType.JIRA);
        return jiraProjectService.listProjects(cfg);
    }

    @PostMapping("/jira/{dataSourceId}/collect")
    public ResponseEntity<String> collectJira(@PathVariable Long dataSourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig cfg = getUserDataSource(userId, dataSourceId, DataSourceType.JIRA);

        int count = jiraCollector.collectIssues(cfg);
        return ResponseEntity.ok("Collected/updated " + count + " Jira issues");
    }

    @PostMapping("/github/{dataSourceId}/repos/{owner}/{repo}/collect")
    public ResponseEntity<String> collectGitHub(
            @PathVariable Long dataSourceId,
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig cfg = getUserDataSource(userId, dataSourceId, DataSourceType.GITHUB_ISSUES);

        String fullName = owner + "/" + repo;
        int count = gitHubIssuesCollector.collectIssuesForRepo(cfg, fullName);
        return ResponseEntity.ok("Collected/updated " + count + " GitHub issues");
    }

    @GetMapping("/{dataSourceId}")
    public Page<IssueDto> listIssues(
            @PathVariable Long dataSourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig cfg = dataSourceService.getDataSource(dataSourceId);

        if (!cfg.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("DataSource does not belong to current user");
        }

        var issuesPage = issueService.getByDataSource(cfg, PageRequest.of(page, size));
        return issuesPage.map(IssueDto::fromEntity);
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

