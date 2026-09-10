package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.service.GitHubAccountLookup;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.github.service.GitHubPrCollector;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
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
    private final GitHubAccountLookup accountLookup;
    private final AuthorIdentityService authorIdentityService;
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
        claimTokenOwnerIdentity(cfg);

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

    /**
     * Links the token owner's GitHub account to the data source owner, so a user who connected
     * GitHub never has to type their login to get PRs, reviews and issues attributed.
     *
     * <p>Best-effort by design: a failure here means one extra API call did not land, and must
     * not fail a collection run that would otherwise succeed. The claim itself is a no-op when
     * the user already has an identity or when another user holds the account.
     */
    private void claimTokenOwnerIdentity(DataSourceConfig cfg) {
        if (cfg.getUser() == null || cfg.getUser().getId() == null) return;
        if (cfg.getApiTokenEncrypted() == null || cfg.getApiTokenEncrypted().isBlank()) return;

        try {
            GitHubAccountLookup.GitHubAccount account = accountLookup.whoAmI(cfg);
            authorIdentityService.claimGithubIdentityIfAbsent(
                    cfg.getUser().getId(), account.id(), account.login());
        } catch (Exception e) {
            log.warn("Could not identify the GitHub token owner for dataSourceId={}: {}",
                    cfg.getId(), e.getMessage());
        }
    }
}
