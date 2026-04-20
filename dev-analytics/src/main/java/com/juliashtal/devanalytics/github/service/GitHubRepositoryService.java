package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GitHubRepositoryService {

    private final GitRepositoryEntityRepository repoRepository;
    private final DataSourceConfigRepository dataSourceRepository;
    private final UserRepository userRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;

    @Transactional
    public GitRepositoryEntity registerGitHubRepo(Long userId, Long dataSourceId, String fullName) {
        User user = userRepository.getReferenceById(userId);

        // Fast path: if the repo already exists in the system (e.g. a manager registered it
        // for a team), any authenticated user can subscribe to it without owning a data source.
        // No collection credentials are needed — commits are already being collected.
        Optional<GitRepositoryEntity> existing = repoRepository.findByRepoFullName(fullName);
        if (existing.isPresent()) {
            GitRepositoryEntity repo = existing.get();
            if (!userRepoRegRepository.existsByUserIdAndRepositoryId(userId, repo.getId())) {
                UserRepoRegistration reg = new UserRepoRegistration();
                reg.setUser(user);
                reg.setRepository(repo);
                userRepoRegRepository.save(reg);
            }
            return repo;
        }

        // Repo doesn't exist yet — the caller must supply their own GITHUB data source
        // so the system knows which credentials to use for collection.
        if (dataSourceId == null) {
            throw new IllegalArgumentException(
                    "dataSourceId is required when registering a new GitHub repo");
        }
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
        repo.setName(fullName);
        repo.setRepoFullName(fullName);
        repo.setLocalPath(null);
        repo.setLastFetchedCommitHash(null);
        repo.setLastScanAt(null);
        repo = repoRepository.save(repo);

        UserRepoRegistration reg = new UserRepoRegistration();
        reg.setUser(user);
        reg.setRepository(repo);
        userRepoRegRepository.save(reg);

        return repo;
    }
}
