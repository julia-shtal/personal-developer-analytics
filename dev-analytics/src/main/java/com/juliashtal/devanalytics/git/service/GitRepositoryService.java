package com.juliashtal.devanalytics.git.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RegisterLocalRepoRequest;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.repository.UserRepository;
import com.juliashtal.devanalytics.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class GitRepositoryService {

    private final GitRepositoryEntityRepository repoRepository;
    private final DataSourceConfigRepository dataSourceRepository;
    private final UserRepository userRepository;
    private final GitCommitEntityRepository commitRepository;

    public GitRepositoryService(GitRepositoryEntityRepository repoRepository,
                                DataSourceConfigRepository dataSourceRepository,
                                UserRepository userRepository, GitCommitEntityRepository commitRepository) {
        this.repoRepository = repoRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.userRepository = userRepository;
        this.commitRepository = commitRepository;
    }

    @Transactional
    public GitRepositoryEntity registerLocalRepo(Long userId, RegisterLocalRepoRequest req) {
        User user = userRepository.getReferenceById(userId);
        DataSourceConfig dataSource = dataSourceRepository.findById(req.getDataSourceId())
                .orElseThrow(() -> new IllegalArgumentException("DataSource not found: " + req.getDataSourceId()));

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

        String normalizedPath = folder.getAbsolutePath();
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

    @Transactional(readOnly = true)
    public List<GitRepositoryEntity> listReposForUser(Long userId) {
        User user = userRepository.getReferenceById(userId);
        // all Git repositories whose DataSource belongs to this user
        return repoRepository.findAll().stream()
                .filter(r -> r.getDataSourceConfig() != null
                        && r.getDataSourceConfig().getUser().getId().equals(user.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GitRepositoryEntity getRepoForUser(Long userId, Long repoId) {
        User user = userRepository.getReferenceById(userId);
        GitRepositoryEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new EntityNotFoundException("Git repo not found: " + repoId));

        if (!repo.getDataSourceConfig().getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Repo does not belong to current user");
        }
        return repo;
    }

    @Transactional(readOnly = true)
    public Page<GitCommitEntity> listCommitsForRepo(Long userId, Long repoId, Pageable pageable) {
        getRepoForUser(userId, repoId);
        return commitRepository.findByRepositoryIdOrderByAuthorDateDesc(repoId, pageable);
    }
}
