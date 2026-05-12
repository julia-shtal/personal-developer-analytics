package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fire-and-forget issues collection for a single repository.
 * Accepts only a repoId so no Hibernate entity is shared across thread boundaries.
 * Uses JOIN FETCH to load dataSourceConfig eagerly in one query.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncIssuesCollectService {

    private final GitHubIssuesCollector issuesCollector;
    private final GitRepositoryEntityRepository gitRepoRepository;

    @Async("collectTaskExecutor")
    @Transactional
    public void collectIssuesForRepo(Long repoId) {
        GitRepositoryEntity repo = gitRepoRepository
                .findByIdWithDataSourceConfig(repoId)
                .orElse(null);

        if (repo == null) {
            log.warn("Async issues collection skipped: repoId={} not found", repoId);
            return;
        }

        log.info("Async issues collection started: repoId={}, repo={}", repoId, repo.getRepoFullName());
        try {
            int count = issuesCollector.collectIssuesForRepo(repo.getDataSourceConfig(), repo);
            log.info("Async issues collection complete: repoId={}, count={}", repoId, count);
        } catch (Exception e) {
            log.error("Async issues collection failed: repoId={}: {}", repoId, e.getMessage(), e);
        }
    }
}
