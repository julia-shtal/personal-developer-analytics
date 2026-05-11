package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.DataSourceResponseDto;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.git.service.GitRepositoryService;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.dto.UpdateDataSourceRequest;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourceService {

    private final DataSourceConfigRepository repository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final SimpleTokenEncryptor tokenEncryptor;
    private final DataSourceValidator validator;
    private final GitRepositoryService gitRepositoryService;
    private final GitHubRepositoryService gitHubRepositoryService;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;

    public DataSourceConfig getDataSource(Long dataSourceId) {
        return repository.findById(dataSourceId)
                .orElseThrow(() -> new NoSuchElementException("DataSource not found: " + dataSourceId));
    }

    @Transactional
    public DataSourceConfig create(Long userId, CreateDataSourceRequest req) {
        validator.validateCreate(req);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        // If the GitHub repo is already registered in the system, do not create a new DS.
        // Instead, subscribe the user to the existing repo under its original DS so it appears
        // in their Data Sources list without a zombie duplicate.
        if ((req.getType() == DataSourceType.GITHUB || req.getType() == DataSourceType.GITHUB_ISSUES)
                && req.getRepoFullName() != null && !req.getRepoFullName().isBlank()) {
            var existingRepo = gitRepoRepository.findByRepoFullName(req.getRepoFullName());
            if (existingRepo.isPresent()) {
                Long existingDsId = existingRepo.get().getDataSourceConfig().getId();
                gitHubRepositoryService.registerGitHubRepo(userId, existingDsId, req.getRepoFullName());
                return null; // No new DS created; controller returns 200 instead of 201
            }
        }

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

        DataSourceConfig saved = repository.save(cfg);

        // Auto-register a GitRepositoryEntity immediately so the user doesn't need
        // a separate step to link the data source to a repository.
        if (saved.getType() == DataSourceType.GIT_LOCAL) {
            // For local repos the path is the repo directory — register it automatically.
            var localReq = new com.juliashtal.devanalytics.git.model.dto.RegisterLocalRepoRequest();
            localReq.setDataSourceId(saved.getId());
            localReq.setName(saved.getName());
            localReq.setLocalPath(saved.getPath());
            try {
                gitRepositoryService.registerLocalRepo(userId, localReq);
            } catch (Exception e) {
                // Non-fatal: DS is saved, repo registration failed (e.g. path issue)
                log.warn("Auto-registration of local repo failed: {}", e.getMessage());
            }
        } else if ((saved.getType() == DataSourceType.GITHUB
                || saved.getType() == DataSourceType.GITHUB_ISSUES)
                && req.getRepoFullName() != null && !req.getRepoFullName().isBlank()) {
            try {
                gitHubRepositoryService.registerGitHubRepo(userId, saved.getId(), req.getRepoFullName());
            } catch (Exception e) {
                log.warn("Auto-registration of GitHub repo failed: {}", e.getMessage());
            }
        }

        return saved;
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

        // DSs the user subscribed to via an existing repo (not owned by them, not team-scoped).
        // These appear as read-only entries: user can sync but cannot delete.
        Set<Long> addedDsIds = new HashSet<>();
        result.forEach(dto -> addedDsIds.add(dto.id()));
        for (DataSourceConfig cfg : userRepoRegRepository.findDataSourceConfigsByUserId(userId)) {
            if (addedDsIds.add(cfg.getId())) {
                result.add(toDto(cfg, false));
            }
        }

        return result;
    }

    private static DataSourceResponseDto toDto(DataSourceConfig cfg, boolean canDelete) {
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
                canDelete
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

        // Allow read access (e.g. for sync) if the user subscribed to a repo in this DS
        if (userRepoRegRepository.existsByUserIdAndDataSourceConfig_Id(userId, id)) {
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
     */
    private boolean canAccessTeam(Long userId, Team team) {
        Role role = SecurityUtils.getCurrentUserRole();
        if (role == Role.ADMIN) return true;
        return team.getManager().getId().equals(userId)
                || teamRepository.existsByIdAndMembersId(team.getId(), userId);
    }

    /**
     * Asserts the current user can manage (create/update/delete) a team data source.
     * Only the team manager or ADMIN may do so.
     */
    private void assertCanManageTeam(Long userId, Team team) {
        Role role = SecurityUtils.getCurrentUserRole();
        if (role == Role.ADMIN) return;
        if (role == Role.MANAGER && team.getManager().getId().equals(userId)) return;
        throw new ForbiddenException("Only the team manager or an admin can manage team data sources");
    }
}
