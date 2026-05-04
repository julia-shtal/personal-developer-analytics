package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.github.service.GitHubPrCollector;
import com.juliashtal.devanalytics.jira.JiraCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourceCollectService {

    private final DataSourceConfigRepository configRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final GitLocalCollector gitLocalCollector;
    private final GitHubCollector gitHubCollector;
    private final GitHubPrCollector prCollector;
    private final GitHubIssuesCollector issuesCollector;
    private final JiraCollector jiraCollector;
    private final DataSourceService dataSourceService;
    private final SyncJobTracker tracker;

    /**
     * Triggers collection for all repositories under the given data source.
     * Each collector runs in its own transaction; no outer transaction is held
     * so we don't keep a DB connection open for the entire (potentially long) job.
     *
     * @param jobState live-progress handle — updated throughout; may be null in tests.
     */
    public String collectForDataSource(Long userId, Long dataSourceId, SyncJobTracker.JobState jobState) {
        DataSourceConfig cfg = dataSourceService.getForUser(userId, dataSourceId);

        int total = 0;
        StringBuilder summary = new StringBuilder();

        // Inform the tracker how many phases this job has so the UI can show "phase N of M".
        if (jobState != null) {
            jobState.totalPhases = switch (cfg.getType()) {
                case GITHUB -> 2;    // commits → pull requests
                default -> 1;
            };
        }

        switch (cfg.getType()) {
            case GIT_LOCAL -> {
                for (var repo : gitRepoRepository.findAllByDataSourceConfig(cfg)) {
                    try {
                        if (jobState != null) tracker.setPhase(jobState, "commits", -1);
                        int n = gitLocalCollector.collectForRepository(repo.getId(), jobState);
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
                        if (jobState != null) tracker.setPhase(jobState, "commits", -1);
                        int commits = gitHubCollector.collectForRepository(repo.getId(), jobState);

                        if (jobState != null) tracker.setPhase(jobState, "pull requests", -1);
                        int prs = prCollector.collectForRepository(repo.getId(), jobState);

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
                        if (jobState != null) tracker.setPhase(jobState, "issues", -1);
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
                    if (jobState != null) tracker.setPhase(jobState, "jira issues", -1);
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
