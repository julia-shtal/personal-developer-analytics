package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.github.service.GitHubPullRequestCollector;
import com.juliashtal.devanalytics.jira.JiraCollector;
import com.juliashtal.devanalytics.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourceCollectService {

    private final DataSourceConfigRepository configRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final GitLocalCollector gitLocalCollector;
    private final GitHubCollector gitHubCollector;
    private final GitHubPullRequestCollector prCollector;
    private final GitHubIssuesCollector issuesCollector;
    private final JiraCollector jiraCollector;
    private final DataSourceService dataSourceService;

    /**
     * Triggers collection for all repositories under the given data source.
     * Returns a human-readable summary of what was collected.
     */
    @Transactional
    public String collectForDataSource(Long dataSourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        DataSourceConfig cfg = dataSourceService.getForUser(userId, dataSourceId);

        int total = 0;
        StringBuilder summary = new StringBuilder();

        switch (cfg.getType()) {
            case GIT_LOCAL -> {
                for (var repo : gitRepoRepository.findAllByDataSourceConfig(cfg)) {
                    try {
                        int n = gitLocalCollector.collectForRepository(repo.getId());
                        total += n;
                        summary.append("Local ").append(repo.getName()).append(": ").append(n).append(" commits. ");
                    } catch (Exception e) {
                        log.warn("Collection failed for repo {}: {}", repo.getId(), e.getMessage());
                    }
                }
            }
            case GITHUB -> {
                for (var repo : gitRepoRepository.findAllByDataSourceConfig(cfg)) {
                    try {
                        int commits = gitHubCollector.collectForRepository(repo.getId());
                        int prs = prCollector.collectPullRequests(userId, repo.getId());
                        total += commits + prs;
                        summary.append(repo.getName()).append(": ").append(commits)
                               .append(" commits, ").append(prs).append(" PRs. ");
                    } catch (Exception e) {
                        log.warn("Collection failed for repo {}: {}", repo.getId(), e.getMessage());
                    }
                }
            }
            case GITHUB_ISSUES -> {
                for (var repo : gitRepoRepository.findAllByDataSourceConfig(cfg)) {
                    try {
                        int n = issuesCollector.collectIssuesForRepo(cfg, repo.getRepoFullName());
                        total += n;
                        summary.append(repo.getName()).append(": ").append(n).append(" issues. ");
                    } catch (Exception e) {
                        log.warn("Issues collection failed for repo {}: {}", repo.getId(), e.getMessage());
                    }
                }
            }
            case JIRA -> {
                try {
                    int n = jiraCollector.collectIssues(cfg);
                    total += n;
                    summary.append("Jira: ").append(n).append(" issues. ");
                } catch (Exception e) {
                    log.warn("Jira collection failed for DS {}: {}", dataSourceId, e.getMessage());
                    summary.append("Jira collection failed: ").append(e.getMessage());
                }
            }
        }

        cfg.setLastSuccessSync(LocalDateTime.now());
        configRepository.save(cfg);

        return summary.isEmpty() ? "Nothing to collect (no repos registered)" : summary.toString().trim();
    }
}
