package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPrReviewEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.kohsuke.github.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class GitHubPullRequestCollector {

    private final GitRepositoryEntityRepository repoRepository;
    private final GitHubPullRequestRepository prRepository;
    private final GitHubPrReviewRepository reviewRepository;
    private final GitHubClientFactory clientFactory;
    private final UserRepository userRepository;

    /**
     * Collects/updates PRs for a single GitHub repository.
     * Strategy: go through all PRs (or the last N) and upsert them by (repo, number).
     */
    @Transactional
    public int collectPullRequests(Long userId, Long gitRepoId) {
        User user = userRepository.getReferenceById(userId);
        GitRepositoryEntity repo = repoRepository.findById(gitRepoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + gitRepoId));

        DataSourceConfig cfg = repo.getDataSourceConfig();
        if (!cfg.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("DataSource does not belong to current user");
        }
        GitHub github = clientFactory.createClient(cfg);

        try {
            GHRepository ghRepo = github.getRepository(repo.getName()); // "owner/repo"

            GHPullRequestQueryBuilder query = ghRepo.queryPullRequests()
                    .state(GHIssueState.ALL); // open + closed

            int processed = 0;

            for (GHPullRequest pr : query.list()) {
                GitHubPullRequestEntity entity = upsertPullRequest(repo, pr);
                upsertReviews(entity, pr);
                processed++;
            }

            cfg.setLastSuccessSync(LocalDateTime.now());
            repoRepository.save(repo);

            return processed;
        } catch (IOException e) {
            throw new GitHubException("Failed to collect GitHub PRs for " + repo.getName(), e);
        }
    }

    private GitHubPullRequestEntity upsertPullRequest(GitRepositoryEntity repo, GHPullRequest pr) throws IOException {
        GitHubPullRequestEntity entity = prRepository
                .findByRepositoryAndNumber(repo, pr.getNumber())
                .orElseGet(GitHubPullRequestEntity::new);

        entity.setRepository(repo);
        entity.setNumber(pr.getNumber());
        entity.setTitle(pr.getTitle());
        entity.setAuthorLogin(pr.getUser() != null ? pr.getUser().getLogin() : null);
        entity.setState(pr.getState().name().toLowerCase());
        entity.setMerged(pr.isMerged());

        entity.setCreatedAt(pr.getCreatedAt());
        entity.setUpdatedAt(pr.getUpdatedAt());
        entity.setClosedAt(pr.getClosedAt());
        entity.setMergedAt(pr.getMergedAt());

        entity.setAdditions(pr.getAdditions());
        entity.setDeletions(pr.getDeletions());
        entity.setChangedFiles(pr.getChangedFiles());
        entity.setCommentsCount(pr.getCommentsCount());
        entity.setReviewCommentsCount(pr.getReviewComments());
        entity.setCommitsCount(pr.getCommits());

        return prRepository.save(entity);
    }

    private void upsertReviews(GitHubPullRequestEntity entity, GHPullRequest pr) throws IOException {
        List<GitHubPrReviewEntity> reviews = new ArrayList<>();
        for (GHPullRequestReview ghReview : pr.listReviews()) {
            Instant submittedAt = ghReview.getSubmittedAt();
            if (submittedAt == null) continue;

            GitHubPrReviewEntity review = new GitHubPrReviewEntity();
            review.setPullRequest(entity);
            review.setReviewerLogin(ghReview.getUser() != null ? ghReview.getUser().getLogin() : null);
            review.setState(ghReview.getState() != null ? ghReview.getState().name() : null);
            review.setSubmittedAt(submittedAt);
            reviews.add(review);
        }

        reviewRepository.deleteAllByPullRequest(entity);
        reviewRepository.saveAll(reviews);
    }

    @Transactional(readOnly = true)
    public Page<GitHubPullRequestEntity> listPullRequests(Long repoId, Pageable pageable) {
        GitRepositoryEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + repoId));
        return prRepository.findByRepositoryOrderByCreatedAtDesc(repo, pageable);
    }
}

