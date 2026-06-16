package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.github.service.GitHubPrCollector;
import com.juliashtal.devanalytics.jira.service.JiraCollector;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

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
    private final JiraProjectService jiraProjectService;
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
        log.info("Collection started: dataSourceId={}, type={}, userId={}", dataSourceId, cfg.getType(), userId);

        // Inform the tracker how many phases this job has so the UI can show "phase N of M".
        if (jobState != null) {
            jobState.totalPhases = countPhases(cfg);
        }

        CollectionResult result = switch (cfg.getType()) {
            case GIT_LOCAL -> collectGitLocalRepos(cfg, jobState);
            case GITHUB -> collectGitHubRepos(cfg, jobState);
            case JIRA -> collectJiraProjects(cfg, dataSourceId, jobState);
        };

        cfg.setLastSuccessSync(LocalDateTime.now());
        configRepository.save(cfg);

        String summary = result.summary().isEmpty()
                ? "Nothing to collect (no repos registered)" : result.summary().trim();
        log.info("Collection finished: dataSourceId={}, total={}, summary={}", dataSourceId, result.total(), summary);
        return summary;
    }

    /**
     * Counts the phases for the job tracker's "phase N of M" display. GitHub jobs always run
     * commits + PRs, plus an issues phase if at least one repo has issue collection enabled.
     */
    private int countPhases(DataSourceConfig cfg) {
        if (Objects.requireNonNull(cfg.getType()) == DataSourceType.GITHUB) {
            var repos = gitRepoRepository.findAllByDataSourceConfig(cfg);
            boolean anyIssues = repos.stream().anyMatch(GitRepositoryEntity::isCollectIssues);
            return anyIssues ? 3 : 2;
        }
        return 1;
    }

    private CollectionResult collectGitLocalRepos(DataSourceConfig cfg, SyncJobTracker.JobState jobState) {
        int total = 0;
        StringBuilder summary = new StringBuilder();
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
        return new CollectionResult(total, summary.toString());
    }

    private CollectionResult collectGitHubRepos(DataSourceConfig cfg, SyncJobTracker.JobState jobState) {
        int total = 0;
        StringBuilder summary = new StringBuilder();
        for (var repo : gitRepoRepository.findAllByDataSourceConfig(cfg)) {
            try {
                if (jobState != null) tracker.setPhase(jobState, "commits", -1);
                int commits = gitHubCollector.collectForRepository(repo.getId(), jobState);

                if (jobState != null) tracker.setPhase(jobState, "pull requests", -1);
                int prs = prCollector.collectForRepository(repo.getId(), jobState);

                int issues = 0;
                if (repo.isCollectIssues()) {
                    if (jobState != null) tracker.setPhase(jobState, "issues", -1);
                    issues = issuesCollector.collectIssuesForRepo(cfg, repo);
                }

                total += commits + prs + issues;
                summary.append(repo.getName()).append(": ").append(commits)
                       .append(" commits, ").append(prs).append(" PRs");
                if (repo.isCollectIssues()) summary.append(", ").append(issues).append(" issues");
                summary.append(". ");
            } catch (Exception e) {
                log.warn("Collection failed for repo {}: {}", repo.getId(), e.getMessage());
            }
        }
        return new CollectionResult(total, summary.toString());
    }

    private CollectionResult collectJiraProjects(DataSourceConfig cfg, Long dataSourceId, SyncJobTracker.JobState jobState) {
        int total = 0;
        StringBuilder summary = new StringBuilder();
        var jiraProjects = jiraProjectService.listTrackedProjects(cfg);
        if (jiraProjects.isEmpty()) {
            log.warn("JIRA datasource {} has no tracked projects — nothing to collect", dataSourceId);
        } else {
            for (var project : jiraProjects) {
                try {
                    if (jobState != null) tracker.setPhase(jobState, "jira issues", -1);
                    int n = jiraCollector.collectIssues(project);
                    total += n;
                    summary.append("Jira[").append(project.getProjectKey()).append("]: ")
                           .append(n).append(" issues. ");
                } catch (Exception e) {
                    log.warn("Jira collection failed for project {}: {}", project.getProjectKey(), e.getMessage());
                    summary.append("Jira[").append(project.getProjectKey())
                           .append("] failed: ").append(e.getMessage()).append(" ");
                }
            }
        }
        return new CollectionResult(total, summary.toString());
    }

    private record CollectionResult(int total, String summary) {}
}
