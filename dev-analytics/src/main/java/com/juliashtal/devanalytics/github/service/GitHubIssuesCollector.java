package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.model.IssueSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects GitHub issues for a repository into the unified issues store.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubIssuesCollector {

    private final GitHubClientFactory clientFactory;
    private final IssueRepository issueRepository;
    private final GitRepositoryEntityRepository gitRepositoryEntityRepository;

    /**
     * Collects issues for a repository entity — updates {@code issuesLastSyncedAt} on success.
     */
    @Transactional
    public int collectIssuesForRepo(DataSourceConfig config, GitRepositoryEntity repo) {
        int count = collectIssuesForRepo(config, repo.getRepoFullName());
        repo.setIssuesLastSyncedAt(Instant.now());
        gitRepositoryEntityRepository.save(repo);
        return count;
    }

    /**
     * Collects issues for a specific repository (name = "owner/repo").
     */
    @Transactional
    public int collectIssuesForRepo(DataSourceConfig config, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("repoFullName is required for GitHub Issues collection");
        }
        log.info("Collecting GitHub issues for repo: {}", fullName);
        GitHub github = clientFactory.createClient(config);

        try {
            GHRepository ghRepo = github.getRepository(fullName);

            // Fetch the repo entity once — not on every issue iteration.
            GitRepositoryEntity repo = gitRepositoryEntityRepository
                    .findByDataSourceConfigAndName(config, fullName)
                    .orElseThrow(() -> new IllegalStateException(
                            "GitRepositoryEntity not found for " + fullName));

            List<IssueEntity> batch = new ArrayList<>();
            for (GHIssue gi : ghRepo.queryIssues().state(GHIssueState.ALL).list().withPageSize(100)) {
                if (gi.getPullRequest() != null) continue; // skip PRs

                IssueEntity issue = buildIssueEntity(config, repo, gi);
                batch.add(issue);
            }

            issueRepository.saveAll(batch);
            log.info("Collected {} issues for repo: {}", batch.size(), fullName);
            return batch.size();
        } catch (IOException e) {
            throw new GitHubException("Failed to collect GitHub issues for " + fullName, e);
        }
    }

    // Package-private for GitHubIssueIdentityMappingTest; the other route needs a live client.
    IssueEntity buildIssueEntity(DataSourceConfig config,
                                         GitRepositoryEntity repo,
                                         GHIssue gi) throws IOException {
        String sourceIssueKey = repo.getRepoFullName() + "#" + gi.getNumber();

        IssueEntity issue = issueRepository
                .findByDataSourceAndSourceIssueKey(config, sourceIssueKey)
                .orElseGet(IssueEntity::new);

        issue.setDataSource(config);
        issue.setRepository(repo);
        issue.setSource(IssueSource.GITHUB);
        issue.setSourceContext(repo.getRepoFullName());
        issue.setSourceIssueKey(sourceIssueKey);

        issue.setTitle(gi.getTitle());
        issue.setDescription(gi.getBody());
        issue.setState(gi.getState().name().toLowerCase());
        issue.setAssignee(gi.getAssignee() != null ? gi.getAssignee().getLogin() : null);
        issue.setCreator(gi.getUser() != null ? gi.getUser().getLogin() : null);
        // Logins above are display values; these IDs are what issue metrics match on.
        issue.setAssigneeGithubId(gi.getAssignee() != null ? gi.getAssignee().getId() : null);
        issue.setCreatorGithubId(gi.getUser() != null ? gi.getUser().getId() : null);
        issue.setCreatedAt(gi.getCreatedAt());
        issue.setUpdatedAt(gi.getUpdatedAt());
        issue.setClosedAt(gi.getClosedAt());

        if (gi.getLabels() != null) {
            String labels = gi.getLabels().stream()
                    .map(GHLabel::getName)
                    .collect(Collectors.joining(","));
            issue.setLabels(labels);
        }

        return issue;
    }
}
