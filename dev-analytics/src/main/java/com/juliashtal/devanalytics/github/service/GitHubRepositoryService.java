package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.repository.UserRepository;
import com.juliashtal.devanalytics.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class GitHubRepositoryService {

    private final GitRepositoryEntityRepository repoRepository;
    private final DataSourceConfigRepository dataSourceRepository;
    private final UserRepository userRepository;

    public GitHubRepositoryService(GitRepositoryEntityRepository repoRepository,
                                   DataSourceConfigRepository dataSourceRepository,
                                   UserRepository userRepository) {
        this.repoRepository = repoRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public GitRepositoryEntity registerGitHubRepo(Long userId, Long dataSourceId, String fullName) {
        User user = userRepository.getReferenceById(userId);
        DataSourceConfig cfg = dataSourceRepository.findById(dataSourceId)
                .orElseThrow(() -> new NoSuchElementException("DataSource not found: " + dataSourceId));

        if (!cfg.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("DataSource does not belong to current user");
        }
        if (cfg.getType() != DataSourceType.GITHUB) {
            throw new IllegalArgumentException("DataSource must be of type GITHUB");
        }

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setDataSourceConfig(cfg);
        repo.setName(fullName);     // "owner/repo"
        repo.setLocalPath(null);
        repo.setLastFetchedCommitHash(null);
        repo.setLastScanAt(null);

        return repoRepository.save(repo);
    }
}
