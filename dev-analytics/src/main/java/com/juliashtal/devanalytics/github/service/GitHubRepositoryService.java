package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.github.model.dto.DiscoveredRepoDto;
import com.juliashtal.devanalytics.github.model.dto.DiscoveryResult;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubRepositoryService {

    private final GitRepositoryEntityRepository repoRepository;
    private final DataSourceConfigRepository dataSourceRepository;
    private final UserRepository userRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;
    private final GitHubClientFactory gitHubClientFactory;

    @Transactional
    public GitRepositoryEntity registerGitHubRepo(Long userId, Long dataSourceId, String fullName) {
        User user = userRepository.getReferenceById(userId);

        Optional<GitRepositoryEntity> existing = repoRepository.findByRepoFullName(fullName);
        return existing.map(gitRepositoryEntity ->
                        subscribeToExistingRepo(user, userId, gitRepositoryEntity, fullName))
                .orElseGet(() -> registerNewGitHubRepo(user, userId, dataSourceId, fullName));

    }

    /**
     * Fast path: the repo already exists in the system (e.g. a manager registered it for a
     * team). Any authenticated user can subscribe to it without owning a data source — no
     * collection credentials are needed since commits are already being collected.
     */
    private GitRepositoryEntity subscribeToExistingRepo(User user, Long userId, GitRepositoryEntity repo, String fullName) {
        if (userRepoRegRepository.findByUserIdAndRepositoryId(userId, repo.getId()).isEmpty()) {
            UserRepoRegistration reg = new UserRepoRegistration();
            reg.setUser(user);
            reg.setRepository(repo);
            userRepoRegRepository.save(reg);
            log.info("User {} subscribed to existing repo: {}", userId, fullName);
        }
        return repo;
    }

    /**
     * Slow path: the repo doesn't exist yet, so the caller must supply their own GITHUB
     * data source so the system knows which credentials to use for collection.
     */
    private GitRepositoryEntity registerNewGitHubRepo(User user, Long userId, Long dataSourceId, String fullName) {
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
        repo.setRepoType(RepoType.GITHUB);
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
        log.info("Registered new GitHub repo: {} for userId={}", fullName, userId);
        return repo;
    }

    /**
     * Discovers all GitHub repositories visible to the datasource's stored token.
     * Each entry is annotated with {@code alreadyAttached=true} if the repo is already
     * tracked under this datasource. Results are cached for 60 seconds per datasource
     * to avoid re-hitting the GitHub API on every UI keypress.
     *
     * <p>If the rate limit is nearly exhausted during pagination, returns the collected
     * repos so far and sets {@code truncated=true} on the result.</p>
     */
    @Cacheable(value = "github-discover-repos", key = "#dataSourceId")
    public DiscoveryResult discoverRepos(Long userId, Long dataSourceId) {
        DataSourceConfig cfg = dataSourceRepository.findById(dataSourceId)
                .orElseThrow(() -> new NotFoundException("DataSource not found: " + dataSourceId));

        if (!cfg.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the datasource owner can discover repositories");
        }
        if (cfg.getType() != DataSourceType.GITHUB) {
            throw new BadRequestException("Only GITHUB datasources support repo discovery");
        }

        Set<String> attached = repoRepository.findAllByDataSourceConfig(cfg).stream()
                .map(GitRepositoryEntity::getRepoFullName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<DiscoveredRepoDto> repos = new ArrayList<>();
        boolean truncated = false;

        GitHub github = gitHubClientFactory.createClient(cfg);
        try {
            for (GHRepository ghRepo : github.getMyself().listRepositories(100)) {
                repos.add(new DiscoveredRepoDto(
                        ghRepo.getFullName(),
                        ghRepo.isPrivate(),
                        ghRepo.getDefaultBranch(),
                        attached.contains(ghRepo.getFullName())
                ));
            }
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.toLowerCase().contains("rate limit") || msg.contains("403") || msg.contains("429")) {
                log.warn("GitHub rate limit hit during discovery for datasource={}", dataSourceId);
                truncated = true;
            } else {
                throw new GitHubException("GitHub repo discovery failed: " + msg, e);
            }
        }

        log.info("Discovered {} repos for datasource={}, truncated={}", repos.size(), dataSourceId, truncated);
        return new DiscoveryResult(repos, truncated);
    }
}
