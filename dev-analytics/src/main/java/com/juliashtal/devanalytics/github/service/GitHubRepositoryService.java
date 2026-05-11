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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
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
            DataSourceConfig cfg = dataSourceId == null ? null
                    : dataSourceRepository.findById(dataSourceId).orElse(null);
            Optional<UserRepoRegistration> existingReg =
                    userRepoRegRepository.findByUserIdAndRepositoryId(userId, repo.getId());
            if (existingReg.isEmpty()) {
                UserRepoRegistration reg = new UserRepoRegistration();
                reg.setUser(user);
                reg.setRepository(repo);
                reg.setDataSourceConfig(cfg);
                userRepoRegRepository.save(reg);
                log.info("User {} subscribed to existing repo: {}", userId, fullName);
            } else if (cfg != null && existingReg.get().getDataSourceConfig() == null) {
                // Registration exists but has no DS link (e.g. created via the manual subscribe
                // button, or the previous DS was deleted and ON DELETE SET NULL fired).
                // Re-link it to the current DS so the repo appears under the dev's DS panel.
                existingReg.get().setDataSourceConfig(cfg);
                userRepoRegRepository.save(existingReg.get());
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
        if (cfg.getType() != DataSourceType.GITHUB && cfg.getType() != DataSourceType.GITHUB_ISSUES) {
            throw new IllegalArgumentException("DataSource must be of type GITHUB or GITHUB_ISSUES");
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
        reg.setDataSourceConfig(cfg);
        userRepoRegRepository.save(reg);
        log.info("Registered new GitHub repo: {} for userId={}", fullName, userId);
        return repo;
    }
}
