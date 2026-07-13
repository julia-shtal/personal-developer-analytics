package com.juliashtal.devanalytics.git.service;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Resolves repositories and the current user's access to them.
 */
@Service
@RequiredArgsConstructor
public class RepoService {

    private final GitRepositoryEntityRepository gitRepoRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;
    private final UserRepository userRepository;

    public GitRepositoryEntity getById(Long repoId) {
        return gitRepoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Git repo not found: " + repoId));
    }

    /** Returns the repo if the user owns it or is subscribed to it; otherwise denies access. */
    public GitRepositoryEntity getAccessibleRepo(Long userId, Long repoId) {
        GitRepositoryEntity repo = getById(repoId);
        boolean owned = repo.getDataSourceConfig().getUser().getId().equals(userId);
        if (!owned && !userRepoRegRepository.existsByUserIdAndRepositoryId(userId, repoId)) {
            throw new ForbiddenException("Access denied to repository: " + repoId);
        }
        return repo;
    }

    public List<RepoDto> listAccessible(Long dataSourceId) {
        return listAccessible(dataSourceId, null);
    }

    public List<RepoDto> listAccessible(Long dataSourceId, Long teamId) {
        Long userId = SecurityUtils.getCurrentUserId();

        // Single view query replaces the three-path merge (owned + subscribed + team).
        // View defined in V38 migration.
        List<Long> repoIds = dataSourceId != null
                ? gitRepoRepository.findAccessibleRepoIdsByDataSource(userId, dataSourceId)
                : gitRepoRepository.findAccessibleRepoIds(userId);

        if (teamId != null) {
            Set<Long> teamRepoIds = new HashSet<>(gitRepoRepository.findIdsByTeamIds(List.of(teamId)));
            repoIds = repoIds.stream().filter(teamRepoIds::contains).toList();
        }

        if (repoIds.isEmpty()) return List.of();

        Set<Long> subscribedIds = new HashSet<>(userRepoRegRepository.findRepoIdsByUserId(userId));
        List<GitRepositoryEntity> repos = gitRepoRepository.findAllByIdWithDataSourceConfig(repoIds);
        return getDtos(repos, subscribedIds);
    }

    public void subscribe(Long repoId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!userRepoRegRepository.existsByUserIdAndRepositoryId(userId, repoId)) {
            GitRepositoryEntity repo = gitRepoRepository.findById(repoId)
                    .orElseThrow(() -> new NoSuchElementException("Repo not found: " + repoId));
            User user = userRepository.getReferenceById(userId);  // proxy sufficient for FK insert
            UserRepoRegistration reg = new UserRepoRegistration();
            reg.setUser(user);
            reg.setRepository(repo);
            userRepoRegRepository.save(reg);
        }
    }

    public void unsubscribe(Long repoId) {
        Long userId = SecurityUtils.getCurrentUserId();
        userRepoRegRepository.findByUserIdAndRepositoryId(userId, repoId)
                .ifPresent(userRepoRegRepository::delete);
    }

    @org.springframework.transaction.annotation.Transactional
    public RepoDto setCollectIssues(Long repoId, boolean enabled,
                                    java.util.function.Consumer<Long> asyncTrigger) {
        Long userId = SecurityUtils.getCurrentUserId();
        GitRepositoryEntity repo = gitRepoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Repo not found: " + repoId));

        repo.setCollectIssues(enabled);
        gitRepoRepository.save(repo);

        Set<Long> subscribedIds = new HashSet<>(userRepoRegRepository.findRepoIdsByUserId(userId));
        RepoDto dto = getDtos(List.of(repo), subscribedIds).get(0);

        // Fire async trigger AFTER the transaction flushes so the new flag is visible to the worker.
        if (enabled) {
            asyncTrigger.accept(repoId);
        }

        return dto;
    }

    /**
     * Converts an API base URL (e.g. https://api.github.com) to its web equivalent
     * (https://github.com) so repo links open the right page in a browser.
     */
    private static String toWebBaseUrl(String apiBaseUrl) {
        String url = apiBaseUrl.strip().replaceAll("/$", "");
        // Standard GitHub public API → public web URL
        if (url.equalsIgnoreCase("https://api.github.com")) {
            return "https://github.com";
        }
        // GitHub Enterprise Server: strip the /api/v3 suffix
        return url.replaceAll("/api/v3$", "");
    }

    private List<RepoDto> getDtos(List<GitRepositoryEntity> repos, Set<Long> subscribedIds) {
        return repos.stream()
                .map(r -> {
                    String repoUrl = null;
                    var dsCfg = r.getDataSourceConfig();
                    if (dsCfg != null && dsCfg.getBaseUrl() != null
                            && r.getRepoType() == RepoType.GITHUB) {
                        repoUrl = toWebBaseUrl(dsCfg.getBaseUrl()) + "/" + r.getRepoFullName();
                    }
                    Long teamId = (dsCfg != null && dsCfg.getTeam() != null) ? dsCfg.getTeam().getId() : null;
                    return new RepoDto(
                            r.getId(),
                            r.getName(),
                            r.getRepoFullName(),
                            r.getLocalPath(),
                            dsCfg != null ? dsCfg.getId() : null,
                            subscribedIds.contains(r.getId()),
                            repoUrl,
                            r.isCollectIssues(),
                            r.getIssuesLastSyncedAt(),
                            teamId
                    );
                })
                .toList();
    }

}
