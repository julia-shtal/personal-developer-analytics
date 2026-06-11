package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.DataSourceResponseDto;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.model.dto.RegisterLocalRepoRequest;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.git.service.GitRepositoryService;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.dto.UpdateDataSourceRequest;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourceService {

    private final DataSourceConfigRepository repository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TokenEncryptor tokenEncryptor;
    private final DataSourceValidator validator;
    private final GitRepositoryService gitRepositoryService;
    private final GitHubRepositoryService gitHubRepositoryService;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;
    private final JiraProjectService jiraProjectService;

    public DataSourceConfig getDataSource(Long dataSourceId) {
        return repository.findById(dataSourceId)
                .orElseThrow(() -> new NoSuchElementException("DataSource not found: " + dataSourceId));
    }

    /**
     * Creates a new datasource for the user. Repo / project attachment is optional:
     * <ul>
     *   <li>GITHUB with {@code repoFullName} present → auto-attaches the repo (or subscribes if it already exists).</li>
     *   <li>GITHUB without {@code repoFullName} → datasource saved with {@code repoCount = 0}; repos attached later via T2.2 endpoints.</li>
     *   <li>JIRA with {@code projectKey} present → auto-creates/subscribes the initial Jira project.</li>
     *   <li>JIRA without {@code projectKey} → datasource saved with no tracked projects.</li>
     * </ul>
     */
    @Transactional
    public DataSourceResponseDto create(Long userId, CreateDataSourceRequest req) {
        validator.validateCreate(req);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        Optional<DataSourceResponseDto> reusedJiraDs = reuseExistingJiraDataSource(userId, req);
        if (reusedJiraDs.isPresent()) {
            return reusedJiraDs.get();
        }

        Optional<DataSourceResponseDto> reusedGitHubDs = reuseExistingGitHubRepo(userId, req);
        if (reusedGitHubDs.isPresent()) {
            return reusedGitHubDs.get();
        }

        DataSourceConfig saved = buildAndSaveDataSource(userId, user, req);

        Optional<DataSourceResponseDto> canonicalJiraDs = autoRegisterAttachedResource(userId, saved, req);
        return canonicalJiraDs.orElseGet(() -> toDto(saved, true));

    }

    /**
     * If a Jira DS for this base URL already exists, subscribes the user instead of creating a
     * duplicate. Checked at the instance level (baseUrl) rather than per-project, so it works even
     * when the caller leaves projectKey blank.
     */
    private Optional<DataSourceResponseDto> reuseExistingJiraDataSource(Long userId, CreateDataSourceRequest req) {
        if (req.getType() != DataSourceType.JIRA || req.getBaseUrl() == null) {
            return Optional.empty();
        }

        var existingProjects = jiraProjectService.findProjectsByBaseUrl(req.getBaseUrl());
        if (existingProjects.isEmpty()) {
            return Optional.empty();
        }

        DataSourceConfig canonicalDs = existingProjects.get(0).getDataSource();
        String normalizedKey = req.getProjectKey() != null
                ? req.getProjectKey().trim().toUpperCase() : null;

        if (normalizedKey != null && !normalizedKey.isBlank()) {
            // Caller specified a project key — subscribe to that project if tracked,
            // otherwise subscribe to all (user can add the missing project later).
            existingProjects.stream()
                    .filter(p -> p.getProjectKey().equals(normalizedKey))
                    .findFirst()
                    .ifPresentOrElse(
                            p -> jiraProjectService.subscribeUser(p.getId(), userId),
                            () -> existingProjects.forEach(
                                    p -> jiraProjectService.subscribeUser(p.getId(), userId)));
        } else {
            // No project key — subscribe to all tracked projects for this Jira instance.
            existingProjects.forEach(p -> jiraProjectService.subscribeUser(p.getId(), userId));
        }

        log.info("User {} subscribed to existing Jira DS {} (baseUrl: {})",
                userId, canonicalDs.getId(), req.getBaseUrl());
        return Optional.of(toDto(canonicalDs, false));
    }

    /**
     * If the GitHub repo is already registered in the system, subscribes the user to the existing
     * repo under its original DS instead of creating a new DS, avoiding a zombie duplicate.
     */
    private Optional<DataSourceResponseDto> reuseExistingGitHubRepo(Long userId, CreateDataSourceRequest req) {
        if (req.getType() != DataSourceType.GITHUB
                || req.getRepoFullName() == null || req.getRepoFullName().isBlank()) {
            return Optional.empty();
        }

        var existingRepo = gitRepoRepository.findByRepoFullName(req.getRepoFullName());
        if (existingRepo.isEmpty()) {
            return Optional.empty();
        }

        Long existingDsId = existingRepo.get().getDataSourceConfig().getId();
        gitHubRepositoryService.registerGitHubRepo(userId, existingDsId, req.getRepoFullName());
        // Convert inside the transaction so lazy proxies are accessible.
        return Optional.of(toDto(existingRepo.get().getDataSourceConfig(), false));
    }

    private DataSourceConfig buildAndSaveDataSource(Long userId, User user, CreateDataSourceRequest req) {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setUser(user);
        cfg.setType(req.getType());
        cfg.setName(req.getName());
        cfg.setBaseUrl(req.getBaseUrl());
        cfg.setPath(req.getPath());
        if (req.getApiToken() != null && !req.getApiToken().isBlank()) {
            cfg.setApiTokenEncrypted(tokenEncryptor.encrypt(req.getApiToken()));
        }
        cfg.setEnabled(true);

        if (req.getTeamId() != null) {
            Team team = teamRepository.findById(req.getTeamId())
                    .orElseThrow(() -> new NoSuchElementException("Team not found: " + req.getTeamId()));
            assertCanManageTeam(userId, team);
            cfg.setTeam(team);
        }

        return repository.save(cfg);
    }

    /**
     * Auto-registers a GitRepositoryEntity or Jira project for a freshly created datasource, based
     * on its type, so the user doesn't need a separate step to link it to a repository/project.
     * For JIRA, if {@code addProject} resolves to a canonical project under a different datasource
     * (the caller omitted projectKey, or a URL normalization edge case slipped through the
     * pre-check), deletes the empty DS just created and returns the canonical one instead.
     */
    private Optional<DataSourceResponseDto> autoRegisterAttachedResource(
            Long userId, DataSourceConfig saved, CreateDataSourceRequest req) {
        if (saved.getType() == DataSourceType.GIT_LOCAL) {
            var localReq = new RegisterLocalRepoRequest();
            localReq.setDataSourceId(saved.getId());
            localReq.setName(saved.getName());
            localReq.setLocalPath(saved.getPath());
            try {
                gitRepositoryService.registerLocalRepo(userId, localReq);
            } catch (Exception e) {
                log.warn("Auto-registration of local repo failed: {}", e.getMessage());
            }
        } else if (saved.getType() == DataSourceType.GITHUB
                && req.getRepoFullName() != null && !req.getRepoFullName().isBlank()) {
            try {
                gitHubRepositoryService.registerGitHubRepo(userId, saved.getId(), req.getRepoFullName());
            } catch (Exception e) {
                log.warn("Auto-registration of GitHub repo failed: {}", e.getMessage());
            }
        } else if (saved.getType() == DataSourceType.JIRA
                && req.getProjectKey() != null && !req.getProjectKey().isBlank()) {
            try {
                var project = jiraProjectService.addProject(saved, req.getProjectKey(), null);
                if (!project.getDataSource().getId().equals(saved.getId())) {
                    jiraProjectService.subscribeUser(project.getId(), userId);
                    repository.delete(saved);
                    return Optional.of(toDto(project.getDataSource(), false));
                }
            } catch (Exception e) {
                log.warn("Auto-creation of initial Jira project failed: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<DataSourceResponseDto> listForUser(Long userId) {
        Role role = SecurityUtils.getCurrentUserRole();
        User user = userRepository.getReferenceById(userId);
        List<DataSourceResponseDto> result = new ArrayList<>();

        // Personal (non-team) data sources — creator can always delete.
        // Team-scoped configs are handled below, even if the manager is also the creator,
        // to avoid showing the same config twice.
        for (DataSourceConfig cfg : repository.findAllByUserAndTeamIsNull(user)) {
            result.add(toDto(cfg, true));
        }

        // Team-scoped configs — deduplicate across member + manager queries
        Set<Long> addedTeamIds = new HashSet<>();
        for (Team team : teamRepository.findByMembersId(userId)) {
            if (!addedTeamIds.add(team.getId())) continue;
            boolean canDel = role == Role.ADMIN || team.getManager().getId().equals(userId);
            for (DataSourceConfig cfg : repository.findAllByTeam(team)) {
                result.add(toDto(cfg, canDel));
            }
        }
        for (Team team : teamRepository.findByManagerId(userId)) {
            if (!addedTeamIds.add(team.getId())) continue;
            for (DataSourceConfig cfg : repository.findAllByTeam(team)) {
                result.add(toDto(cfg, true));
            }
        }

        // DSs the user subscribed to via a repo or Jira project (not owned, not team-scoped).
        // These appear as read-only entries: user can sync but cannot delete.
        Set<Long> addedDsIds = new HashSet<>();
        result.forEach(dto -> addedDsIds.add(dto.id()));
        for (DataSourceConfig cfg : userRepoRegRepository.findDataSourceConfigsByUserId(userId)) {
            if (addedDsIds.add(cfg.getId())) {
                result.add(toDto(cfg, false));
            }
        }
        for (DataSourceConfig cfg : jiraProjectService.findSubscribedDataSourceConfigs(userId)) {
            if (addedDsIds.add(cfg.getId())) {
                result.add(toDto(cfg, false));
            }
        }

        return result;
    }

    private DataSourceResponseDto toDto(DataSourceConfig cfg, boolean canDelete) {
        return new DataSourceResponseDto(
                cfg.getId(),
                cfg.getType(),
                cfg.getName(),
                cfg.getBaseUrl(),
                cfg.getPath(),
                cfg.isEnabled(),
                cfg.getLastSuccessSync(),
                cfg.getCreatedAt(),
                cfg.getTeam() != null ? cfg.getTeam().getId() : null,
                canDelete,
                gitRepoRepository.countByDataSourceConfig(cfg)
        );
    }

    @Transactional(readOnly = true)
    public DataSourceConfig getForUser(Long userId, Long id) {
        User user = userRepository.getReferenceById(userId);
        // Try user-owned first
        var cfg = repository.findByIdAndUser(id, user);
        if (cfg.isPresent()) {
            return cfg.get();
        }
        // Try team-scoped — user must be a member or manager of the owning team
        DataSourceConfig teamCfg = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("DataSource not found: " + id));

        if (teamCfg.getTeam() != null && canAccessTeam(userId, teamCfg.getTeam())) {
            return teamCfg;
        }

        // Allow read access (e.g. for sync) if the user subscribed to a repo or Jira project in this DS
        if (userRepoRegRepository.existsByUserIdAndDataSourceConfig_Id(userId, id)) {
            return teamCfg;
        }
        if (jiraProjectService.hasSubscriptionForDataSource(userId, id)) {
            return teamCfg;
        }

        throw new NoSuchElementException("DataSource not found: " + id);
    }

    @Transactional
    public DataSourceConfig update(Long userId, Long id, UpdateDataSourceRequest req) {
        DataSourceConfig cfg = loadForWrite(userId, id);

        if (req.getName() != null) {
            cfg.setName(req.getName());
        }
        if (req.getBaseUrl() != null) {
            cfg.setBaseUrl(req.getBaseUrl());
        }
        if (req.getPath() != null) {
            cfg.setPath(req.getPath());
        }
        if (req.getApiToken() != null) {
            cfg.setApiTokenEncrypted(tokenEncryptor.encrypt(req.getApiToken()));
        }
        if (req.getEnabled() != null) {
            cfg.setEnabled(req.getEnabled());
        }

        return repository.save(cfg);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        DataSourceConfig cfg = loadForWrite(userId, id);
        repository.delete(cfg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Repo attach / detach / list
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attaches a GitHub repository to an existing datasource. Idempotent: if the repo
     * is already attached to this DS, returns the existing {@link RepoDto} without creating a duplicate.
     * Throws {@link ConflictException} (409) if the repo is already tracked by a different datasource.
     *
     * @return the attached repo; callers should distinguish 201 (new) vs 200 (existing) by checking
     *         whether the returned id was newly created.
     */
    @Transactional
    public RepoDto attachRepo(Long userId, Long dataSourceId, String repoFullName, boolean collectIssues) {
        DataSourceConfig cfg = loadForWrite(userId, dataSourceId);
        if (cfg.getType() != DataSourceType.GITHUB) {
            throw new BadRequestException("Only GITHUB datasources support repo attachment");
        }

        var existing = gitRepoRepository.findByRepoFullName(repoFullName);
        if (existing.isPresent()) {
            GitRepositoryEntity repo = existing.get();
            if (repo.getDataSourceConfig().getId().equals(dataSourceId)) {
                // Same DS — idempotent return.
                return toRepoDto(repo, userId);
            }
            // Repo is canonical under a different DS. Subscribe the user and clean up the
            // calling DS if it has no canonical repos of its own (orphaned empty DS).
            if (userRepoRegRepository.findByUserIdAndRepositoryId(userId, repo.getId()).isEmpty()) {
                UserRepoRegistration reg = new UserRepoRegistration();
                reg.setUser(userRepository.getReferenceById(userId));
                reg.setRepository(repo);
                userRepoRegRepository.save(reg);
                log.info("User {} subscribed to existing repo {} (canonical DS={})",
                        userId, repoFullName, repo.getDataSourceConfig().getId());
            }
            if (gitRepoRepository.countByDataSourceConfig(cfg) == 0) {
                log.info("Deleting empty orphaned GitHub DS {} after cross-DS attach", cfg.getId());
                repository.delete(cfg);
            }
            return toRepoDto(repo, userId);
        }

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setDataSourceConfig(cfg);
        repo.setRepoType(RepoType.GITHUB);
        repo.setName(repoFullName);
        repo.setRepoFullName(repoFullName);
        repo.setCollectIssues(collectIssues);
        repo = gitRepoRepository.save(repo);

        UserRepoRegistration reg = new UserRepoRegistration();
        reg.setUser(userRepository.getReferenceById(userId));
        reg.setRepository(repo);
        userRepoRegRepository.save(reg);

        log.info("Attached repo {} to datasource {} for userId={}", repoFullName, dataSourceId, userId);
        return toRepoDto(repo, userId);
    }

    /**
     * Detaches a repository from a datasource. Throws {@link ConflictException} (409) when other
     * users are still subscribed to the repo — they must unsubscribe first.
     */
    @Transactional
    public void detachRepo(Long userId, Long dataSourceId, Long repoId) {
        loadForWrite(userId, dataSourceId);

        GitRepositoryEntity repo = gitRepoRepository.findById(repoId)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + repoId));
        if (!repo.getDataSourceConfig().getId().equals(dataSourceId)) {
            throw new ForbiddenException("Repository " + repoId + " does not belong to datasource " + dataSourceId);
        }

        long otherSubscribers = userRepoRegRepository.countSubscribersExcludingUser(repoId, userId);
        if (otherSubscribers > 0) {
            throw new ConflictException(
                    "Repository has " + otherSubscribers + " active subscription(s). " +
                    "All subscribers must unsubscribe before the repo can be detached.");
        }

        gitRepoRepository.delete(repo);
        log.info("Detached repo {} from datasource {} by userId={}", repoId, dataSourceId, userId);
    }

    /** Lists all repositories attached to a datasource. Accessible to DS owners and subscribers. */
    @Transactional(readOnly = true)
    public List<RepoDto> listReposForDataSource(Long userId, Long dataSourceId) {
        DataSourceConfig cfg = getForUser(userId, dataSourceId);
        Set<Long> subscribedIds = new HashSet<>(userRepoRegRepository.findRepoIdsByUserId(userId));
        return gitRepoRepository.findAllByDataSourceConfig(cfg).stream()
                .map(r -> toRepoDtoWithSubscribed(r, subscribedIds))
                .toList();
    }

    private RepoDto toRepoDto(GitRepositoryEntity r, Long userId) {
        Set<Long> sub = new HashSet<>(userRepoRegRepository.findRepoIdsByUserId(userId));
        return toRepoDtoWithSubscribed(r, sub);
    }

    private RepoDto toRepoDtoWithSubscribed(GitRepositoryEntity r, Set<Long> subscribedIds) {
        String repoUrl = null;
        var dsCfg = r.getDataSourceConfig();
        if (dsCfg != null && dsCfg.getBaseUrl() != null
                && r.getRepoType() == RepoType.GITHUB) {
            repoUrl = githubWebUrl(dsCfg.getBaseUrl()) + "/" + r.getRepoFullName();
        }
        Long teamId = (dsCfg != null && dsCfg.getTeam() != null) ? dsCfg.getTeam().getId() : null;
        return new RepoDto(r.getId(), r.getName(), r.getRepoFullName(), r.getLocalPath(),
                dsCfg != null ? dsCfg.getId() : null,
                subscribedIds.contains(r.getId()),
                repoUrl, r.isCollectIssues(), r.getIssuesLastSyncedAt(), teamId);
    }

    private static String githubWebUrl(String apiBaseUrl) {
        String url = apiBaseUrl.strip().replaceAll("/$", "");
        if (url.equalsIgnoreCase("https://api.github.com")) return "https://github.com";
        return url.replaceAll("/api/v3$", "");
    }

    /**
     * Loads a config that the current user is allowed to modify.
     * Creator always can; for team-scoped configs the user must also be the team manager or ADMIN.
     */
    private DataSourceConfig loadForWrite(Long userId, Long id) {
        DataSourceConfig cfg = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("DataSource not found: " + id));

        // Creator can always modify
        if (cfg.getUser() != null && cfg.getUser().getId().equals(userId)) {
            return cfg;
        }

        // For team-scoped configs, only the team manager or ADMIN can modify
        if (cfg.getTeam() != null) {
            assertCanManageTeam(userId, cfg.getTeam());
            return cfg;
        }

        throw new ForbiddenException("Access denied to DataSource: " + id);
    }

    /**
     * Returns true if the user can read data from a team-scoped data source
     * (i.e. is a team member, the team manager, or an ADMIN).
     * Uses a DB role lookup so this is safe to call from async threads (no SecurityContext needed).
     */
    private boolean canAccessTeam(Long userId, Team team) {
        Role role = userRepository.getReferenceById(userId).getRole();
        if (role == Role.ADMIN) return true;
        return team.getManager().getId().equals(userId)
                || teamRepository.existsByIdAndMembersId(team.getId(), userId);
    }

    /**
     * Asserts the current user can manage (create/update/delete) a team data source.
     * Only the team manager or ADMIN may do so.
     * Uses a DB role lookup so this is safe to call from async threads (no SecurityContext needed).
     */
    private void assertCanManageTeam(Long userId, Team team) {
        Role role = userRepository.getReferenceById(userId).getRole();
        if (role == Role.ADMIN) return;
        if (role == Role.MANAGER && team.getManager().getId().equals(userId)) return;
        throw new ForbiddenException("Only the team manager or an admin can manage team data sources");
    }
}
