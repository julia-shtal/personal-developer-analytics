package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import org.kohsuke.github.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class GitHubPullRequestCollector {

    private final GitRepositoryEntityRepository repoRepository;
    private final GitHubPullRequestRepository prRepository;
    private final GitHubClientFactory clientFactory;

    public GitHubPullRequestCollector(GitRepositoryEntityRepository repoRepository,
                                      GitHubPullRequestRepository prRepository,
                                      GitHubClientFactory clientFactory) {
        this.repoRepository = repoRepository;
        this.prRepository = prRepository;
        this.clientFactory = clientFactory;
    }

    /**
     * Collects/updates PRs for a single GitHub repository.
     * Strategy: go through all PRs (or the last N) and upsert them by (repo, number).
     */
    @Transactional
    public int collectPullRequests(Long gitRepoId) {
        GitRepositoryEntity repo = repoRepository.findById(gitRepoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + gitRepoId));

        DataSourceConfig cfg = repo.getDataSourceConfig();
        GitHub github = clientFactory.createClient(cfg);

        try {
            GHRepository ghRepo = github.getRepository(repo.getName()); // "owner/repo"

            GHPullRequestQueryBuilder query = ghRepo.queryPullRequests()
                    .state(GHIssueState.ALL); // open + closed

            int processed = 0;

            for (GHPullRequest pr : query.list()) {
                upsertPullRequest(repo, pr);
                processed++;
            }

            cfg.setLastSuccessSync(LocalDateTime.now());
            repoRepository.save(repo);

            return processed;
        } catch (IOException e) {
            throw new GitHubException("Failed to collect GitHub PRs for " + repo.getName(), e);
        }
    }

    private void upsertPullRequest(GitRepositoryEntity repo, GHPullRequest pr) throws IOException {
        GitHubPullRequestEntity entity = prRepository
                .findByRepositoryAndNumber(repo, pr.getNumber())
                .orElseGet(GitHubPullRequestEntity::new);

        entity.setRepository(repo);
        entity.setNumber(pr.getNumber());
        entity.setTitle(pr.getTitle());
        entity.setAuthorLogin(pr.getUser() != null ? pr.getUser().getLogin() : null);
        entity.setState(pr.getState().name().toLowerCase()); // OPEN/CLOSED → open/closed
        entity.setMerged(pr.isMerged());

        entity.setCreatedAt(toInstant(pr.getCreatedAt()));
        entity.setUpdatedAt(toInstant(pr.getUpdatedAt()));
        entity.setClosedAt(toInstant(pr.getClosedAt()));
        entity.setMergedAt(toInstant(pr.getMergedAt()));

        entity.setAdditions(pr.getAdditions());
        entity.setDeletions(pr.getDeletions());
        entity.setChangedFiles(pr.getChangedFiles());
        entity.setCommentsCount(pr.getCommentsCount());
        entity.setReviewCommentsCount(pr.getReviewComments());
        entity.setCommitsCount(pr.getCommits());

        prRepository.save(entity);
    }

    private Instant toInstant(java.util.Date date) {
        return date != null ? date.toInstant() : null;
    }

    @Transactional(readOnly = true)
    public Page<GitHubPullRequestEntity> listPullRequests(Long gitRepoId, Pageable pageable) {
        GitRepositoryEntity repo = repoRepository.findById(gitRepoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + gitRepoId));
        return prRepository.findByRepositoryOrderByCreatedAtDesc(repo, pageable);
    }
}

