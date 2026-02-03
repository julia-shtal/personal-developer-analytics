package com.juliashtal.devanalytics.github;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHubPullRequestRepository extends JpaRepository<GitHubPullRequestEntity, Long> {
    Optional<GitHubPullRequestEntity> findByRepositoryAndNumber(GitRepositoryEntity repository, int number);
    Page<GitHubPullRequestEntity> findByRepositoryOrderByCreatedAtDesc(GitRepositoryEntity repository, Pageable pageable);
}

