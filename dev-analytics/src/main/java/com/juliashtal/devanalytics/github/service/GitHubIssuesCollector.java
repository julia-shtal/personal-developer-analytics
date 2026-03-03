package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.kohsuke.github.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GitHubIssuesCollector {

    private final GitHubClientFactory clientFactory;
    private final IssueRepository issueRepository;
    private final GitRepositoryEntityRepository  gitRepositoryEntityRepository;

    /**
     * Collects issues for a specific repository (name = “owner/repo”).
     */
    @Transactional
    public int collectIssuesForRepo(DataSourceConfig config, String fullName) {
        GitHub github = clientFactory.createClient(config);

        try {
            GHRepository ghRepo = github.getRepository(fullName);

            // open + closed issues, except PR’s.
            List<GHIssue> issues = ghRepo.getIssues(GHIssueState.ALL).stream()
                    .filter(i -> i.getPullRequest() == null)
                    .toList();

            int saved = 0;
            for (GHIssue gi : issues) {
                upsertGitHubIssue(config, ghRepo, gi);
                saved++;
            }
            return saved;
        } catch (IOException e) {
            throw new GitHubException("Failed to collect GitHub issues for " + fullName, e);
        }
    }

    private void upsertGitHubIssue(DataSourceConfig config,
                                   GHRepository ghRepo,
                                   GHIssue gi) throws IOException {

        String fullName = ghRepo.getFullName(); // "owner/repo"
        String externalId = fullName + "#" + gi.getNumber();

        GitRepositoryEntity repo = gitRepositoryEntityRepository
                .findByDataSourceConfigAndName(config, fullName)
                .orElseThrow(() -> new IllegalStateException(
                        "GitRepositoryEntity not found for " + fullName));

        IssueEntity issue = issueRepository
                .findByDataSourceAndExternalId(config, externalId)
                .orElseGet(IssueEntity::new);

        issue.setDataSource(config);
        issue.setRepository(repo);
        issue.setExternalId(externalId);

        issue.setTitle(gi.getTitle());
        issue.setDescription(gi.getBody());
        issue.setState(gi.getState().name().toLowerCase());
        issue.setAssignee(gi.getAssignee() != null ? gi.getAssignee().getLogin() : null);
        issue.setCreator(gi.getUser() != null ? gi.getUser().getLogin() : null);
        issue.setCreatedAt(gi.getCreatedAt());
        issue.setUpdatedAt(gi.getUpdatedAt());
        issue.setClosedAt(gi.getClosedAt());

        if (gi.getLabels() != null) {
            String labels = gi.getLabels().stream()
                    .map(GHLabel::getName)
                    .collect(Collectors.joining(","));
            issue.setLabels(labels);
        }

        issueRepository.save(issue);
    }

}

