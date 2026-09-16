package com.juliashtal.devanalytics.github.pullrequest;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Read side for stored pull requests.
 * Separate from ingestion so neither half carries the other's dependencies.
 */
@Service
@RequiredArgsConstructor
public class GitHubPullRequestQueryService {

    private final GitRepositoryEntityRepository repoRepository;
    private final GitHubPullRequestRepository prRepository;

    @Transactional(readOnly = true)
    public Page<GitHubPullRequestEntity> listPullRequests(Long repoId, Pageable pageable) {
        GitRepositoryEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + repoId));
        return prRepository.findByRepositoryOrderByCreatedAtDesc(repo, pageable);
    }
}
