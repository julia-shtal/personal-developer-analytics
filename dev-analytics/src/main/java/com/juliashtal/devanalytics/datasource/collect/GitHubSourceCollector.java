package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.github.service.GitHubPrCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link SourceCollector} adapter for {@link DataSourceType#GITHUB}.
 * Runs commits → PRs → (conditional) issues in sequence.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubSourceCollector implements SourceCollector {

    private final GitRepositoryEntityRepository gitRepoRepository;
    private final GitHubCollector gitHubCollector;
    private final GitHubPrCollector prCollector;
    private final GitHubIssuesCollector issuesCollector;
    private final SyncJobTracker tracker;

    @Override
    public DataSourceType supports() {
        return DataSourceType.GITHUB;
    }

    @Override
    public int phaseCount(DataSourceConfig cfg) {
        var repos = gitRepoRepository.findAllByDataSourceConfig(cfg);
        boolean anyIssues = repos.stream().anyMatch(GitRepositoryEntity::isCollectIssues);
        return anyIssues ? 3 : 2;
    }

    @Override
    public int collect(DataSourceConfig cfg, SyncJobTracker.JobState jobState) {
        int total = 0;
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
            } catch (Exception e) {
                log.warn("Collection failed for repo {}: {}", repo.getId(), e.getMessage());
            }
        }
        return total;
    }
}
