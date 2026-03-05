package com.juliashtal.devanalytics.git.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.dto.RegisterLocalRepoRequest;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.user.UserRepository;
import com.juliashtal.devanalytics.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class GitRepositoryService {

    private final GitRepositoryEntityRepository repoRepository;
    private final DataSourceConfigRepository dataSourceRepository;
    private final UserRepository userRepository;
    private final GitCommitEntityRepository commitRepository;

    @Transactional
    public GitRepositoryEntity registerLocalRepo(Long userId, RegisterLocalRepoRequest req) {
        User user = userRepository.getReferenceById(userId);
        DataSourceConfig dataSource = dataSourceRepository.findById(req.getDataSourceId())
                .orElseThrow(() -> new NoSuchElementException("DataSource not found: " + req.getDataSourceId()));

        String normalizedPath = getNormalizedPath(req, dataSource, user);
        repoRepository.findAllByDataSourceConfig(dataSource).stream()
                .filter(r -> normalizedPath.equals(r.getLocalPath()))
                .findFirst()
                .ifPresent(r -> {
                    throw new IllegalArgumentException("Repository with this path already registered for this data source");
                });

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setDataSourceConfig(dataSource);
        repo.setName(req.getName());
        repo.setLocalPath(normalizedPath);
        repo.setLastFetchedCommitHash(null);
        repo.setLastScanAt(LocalDateTime.now());

        return repoRepository.save(repo);
    }

    private static String getNormalizedPath(RegisterLocalRepoRequest req, DataSourceConfig dataSource, User user) {
        if (!dataSource.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("DataSource does not belong to current user");
        }
        if (dataSource.getType() != DataSourceType.GIT_LOCAL) {
            throw new IllegalArgumentException("DataSource must be of type GIT_LOCAL");
        }

        File folder = new File(req.getLocalPath());
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("Local path is not a directory: " + req.getLocalPath());
        }

        return folder.getAbsolutePath();
    }

    @Transactional(readOnly = true)
    public List<GitRepositoryEntity> listReposForUser(Long userId) {
        User user = userRepository.getReferenceById(userId);
        return repoRepository.findAll().stream()
                .filter(r -> r.getDataSourceConfig() != null
                        && r.getDataSourceConfig().getUser().getId().equals(user.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GitRepositoryEntity getRepoForUser(Long userId, Long repoId) {
        GitRepositoryEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + repoId));
        checkRepoForUser(userId, repo);
        return repo;
    }

    @Transactional(readOnly = true)
    public Page<GitCommitEntity> listCommitsForRepo(Long userId, Long repoId, Pageable pageable) {
        GitRepositoryEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + repoId));
        checkRepoForUser(userId, repo);
        return commitRepository.findByRepositoryIdOrderByAuthorDateDesc(repoId, pageable);
    }

    private void checkRepoForUser(Long userId, GitRepositoryEntity repo) {
        User user = userRepository.getReferenceById(userId);
        if (!repo.getDataSourceConfig().getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Repo does not belong to current user");
        }
    }
}
