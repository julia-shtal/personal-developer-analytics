package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.identity.GitHubAccountLookup;
import com.juliashtal.devanalytics.github.commit.GitHubCommitCollector;
import com.juliashtal.devanalytics.github.issue.GitHubIssuesCollector;
import com.juliashtal.devanalytics.github.pullrequest.GitHubPullRequestCollector;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * {@link SourceCollector} adapter for {@link DataSourceType#GITHUB}.
 * Runs commits → PRs → (conditional) issues in sequence.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubSourceCollector implements SourceCollector {

    private final GitRepositoryEntityRepository gitRepoRepository;
    private final GitHubCommitCollector commitCollector;
    private final GitHubPullRequestCollector prCollector;
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
            total += runStage(repo, "commits", jobState,
                    () -> commitCollector.collectForRepository(repo.getId(), jobState));
            total += runStage(repo, "pull requests", jobState,
                    () -> prCollector.collectForRepository(repo.getId(), jobState));
            if (repo.isCollectIssues()) {
                total += runStage(repo, "issues", jobState,
                        () -> issuesCollector.collectIssuesForRepo(cfg, repo));
            }
        }
        return total;
    }

    /**
     * Runs one stage, so that its failure costs only its own result.
     *
     * <p>The three stages share no transaction and none reads another's output, so a stage that
     * throws must not discard what the stages before it already collected and persisted.</p>
     */
    private int runStage(GitRepositoryEntity repo, String phase, SyncJobTracker.JobState jobState,
                         Callable<Integer> stage) {
        if (jobState != null) tracker.setPhase(jobState, phase, -1);
        try {
            return stage.call();
        } catch (Exception e) {
            log.error("Collection stage '{}' failed for repo {} ({}): {}",
                    phase, repo.getId(), repo.getRepoFullName(), e.getMessage(), e);
            return 0;
        }
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
