package com.juliashtal.devanalytics.git.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RepoService {

    private final GitRepositoryEntityRepository gitRepoRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;
    private final DataSourceConfigRepository dataSourceConfigRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public List<RepoDto> listAccessible(Long dataSourceId) {
        Long userId = SecurityUtils.getCurrentUserId();

        Set<Long> subscribedIds = new HashSet<>(userRepoRegRepository.findRepoIdsByUserId(userId));

        // Collect repos from own data sources + team data sources
        List<GitRepositoryEntity> repos = new ArrayList<>();
        Set<Long> seenRepoIds = new HashSet<>();

        for (DataSourceConfig cfg : dataSourceConfigRepository.findAllByUserId(userId)) {
            if (dataSourceId != null && !cfg.getId().equals(dataSourceId)) continue;
            for (GitRepositoryEntity repo : gitRepoRepository.findAllByDataSourceConfig(cfg)) {
                if (seenRepoIds.add(repo.getId())) repos.add(repo);
            }
        }

        Set<Long> visitedTeamIds = new HashSet<>();
        List<Team> teams = new ArrayList<>();
        teams.addAll(teamRepository.findByMembersId(userId));
        teams.addAll(teamRepository.findByManagerId(userId));
        for (Team team : teams) {
            if (!visitedTeamIds.add(team.getId())) continue;
            for (DataSourceConfig cfg : dataSourceConfigRepository.findAllByTeam(team)) {
                if (dataSourceId != null && !cfg.getId().equals(dataSourceId)) continue;
                for (GitRepositoryEntity repo : gitRepoRepository.findAllByDataSourceConfig(cfg)) {
                    if (seenRepoIds.add(repo.getId())) repos.add(repo);
                }
            }
        }

        Set<Long> unseenSubscribed = filterUnseenSubscribed(dataSourceId, userId, seenRepoIds, subscribedIds);
        if (!unseenSubscribed.isEmpty()) {
            for (GitRepositoryEntity repo : gitRepoRepository.findAllByIdWithDataSourceConfig(unseenSubscribed)) {
                if (seenRepoIds.add(repo.getId())) repos.add(repo);
            }
        }
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
                    if (dsCfg != null && dsCfg.getBaseUrl() != null && r.getRepoFullName() != null
                            && (dsCfg.getType() == DataSourceType.GITHUB
                            || dsCfg.getType() == DataSourceType.GITHUB_ISSUES)) {
                        repoUrl = toWebBaseUrl(dsCfg.getBaseUrl()) + "/" + r.getRepoFullName();
                    }
                    return new RepoDto(
                            r.getId(),
                            r.getName(),
                            r.getRepoFullName(),
                            r.getLocalPath(),
                            dsCfg != null ? dsCfg.getId() : null,
                            subscribedIds.contains(r.getId()),
                            repoUrl
                    );
                })
                .toList();
    }

    private Set<Long> filterUnseenSubscribed(Long dataSourceId, Long userId, Set<Long> seenRepoIds, Set<Long> subscribedIds) {
        // Include repos the user is subscribed to that weren't surfaced by the data-source loops.
        // This covers the case where registerGitHubRepo found an existing repo under *another*
        // user's data source: a UserRepoRegistration was created (with data_source_id set to the
        // user's own data source), but the repo's dataSourceConfig still points to the original
        // owner's data source — so findAllByDataSourceConfig above returns nothing for the current
        // user's config.
        // When filtering by a specific data source, only include repos whose subscription was
        // created through that exact data source to prevent repos from other data sources bleeding
        // across.
        Set<Long> unseenSubscribed;
        if (dataSourceId != null) {
            unseenSubscribed = new HashSet<>(
                    userRepoRegRepository.findRepoIdsByUserIdAndDataSourceId(userId, dataSourceId));
            unseenSubscribed.removeAll(seenRepoIds);
        } else {
            unseenSubscribed = new HashSet<>(subscribedIds);
            unseenSubscribed.removeAll(seenRepoIds);
        }
        return unseenSubscribed;
    }
}
