package com.juliashtal.devanalytics.git;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Unified repo subscription API.
 *
 * GET  /api/repos                  — list all repos the user can see (own + team), with subscribed flag
 * POST /api/repos/{id}/subscribe   — create a UserRepoRegistration (subscribe)
 * DELETE /api/repos/{id}/subscribe — remove a UserRepoRegistration (unsubscribe)
 */
@RestController
@RequestMapping("/api/repos")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class RepoController {

    private final GitRepositoryEntityRepository gitRepoRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;
    private final DataSourceConfigRepository dataSourceConfigRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @GetMapping
    public List<RepoDto> listAccessible(
            @RequestParam(required = false) Long dataSourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.getReferenceById(userId);

        Set<Long> subscribedIds = new HashSet<>(userRepoRegRepository.findRepoIdsByUserId(userId));

        // Collect repos from own data sources + team data sources
        List<GitRepositoryEntity> repos = new ArrayList<>();
        Set<Long> seenRepoIds = new HashSet<>();

        for (DataSourceConfig cfg : dataSourceConfigRepository.findAllByUser(user)) {
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

        return repos.stream()
                .map(r -> {
                    String repoUrl = null;
                    var dsCfg = r.getDataSourceConfig();
                    if (dsCfg != null && dsCfg.getBaseUrl() != null && r.getRepoFullName() != null
                            && (dsCfg.getType() == DataSourceType.GITHUB
                                || dsCfg.getType() == DataSourceType.GITHUB_ISSUES)) {
                        repoUrl = dsCfg.getBaseUrl().stripTrailing().replaceAll("/$", "")
                                  + "/" + r.getRepoFullName();
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

    @PostMapping("/{repoId}/subscribe")
    public ResponseEntity<Void> subscribe(@PathVariable Long repoId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!userRepoRegRepository.existsByUserIdAndRepositoryId(userId, repoId)) {
            GitRepositoryEntity repo = gitRepoRepository.findById(repoId)
                    .orElseThrow(() -> new NoSuchElementException("Repo not found: " + repoId));
            User user = userRepository.getReferenceById(userId);
            UserRepoRegistration reg = new UserRepoRegistration();
            reg.setUser(user);
            reg.setRepository(repo);
            userRepoRegRepository.save(reg);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{repoId}/subscribe")
    public ResponseEntity<Void> unsubscribe(@PathVariable Long repoId) {
        Long userId = SecurityUtils.getCurrentUserId();
        userRepoRegRepository.findByUserIdAndRepositoryId(userId, repoId)
                .ifPresent(userRepoRegRepository::delete);
        return ResponseEntity.noContent().build();
    }
}
