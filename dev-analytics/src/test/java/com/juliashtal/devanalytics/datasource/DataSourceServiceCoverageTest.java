package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.dto.DataSourceResponseDto;
import com.juliashtal.devanalytics.datasource.model.dto.UpdateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.DataSourceValidator;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.git.service.GitRepositoryService;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceCoverageTest {

    @Mock DataSourceConfigRepository repository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock TokenEncryptor tokenEncryptor;
    @Mock DataSourceValidator validator;
    @Mock GitRepositoryService gitRepositoryService;
    @Mock GitHubRepositoryService gitHubRepositoryService;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;
    @Mock JiraProjectService jiraProjectService;

    @InjectMocks DataSourceService service;

    private static final Long USER_ID = 1L;

    private MockedStatic<SecurityUtils> securityUtils;

    @AfterEach
    void tearDown() {
        if (securityUtils != null) {
            securityUtils.close();
        }
    }

    // ── getDataSource ──────────────────────────────────────────────────────

    @Test
    void getDataSource_notFound_throwsNoSuchElementException() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDataSource(404L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getDataSource_found_returnsConfig() {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(cfg));

        DataSourceConfig result = service.getDataSource(5L);

        assertThat(result.getId()).isEqualTo(5L);
    }

    // ── create: reuseExistingGitHubRepo (cross-DS subscribe) ────────────────

    @Test
    void create_githubRepoAlreadyRegisteredUnderDifferentDs_subscribesAndReturnsExistingDs() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(DataSourceType.GITHUB);
        req.setName("Another GitHub DS");
        req.setBaseUrl("https://api.github.com");
        req.setApiToken("ghp_test");
        req.setRepoFullName("owner/already-tracked");

        DataSourceConfig existingDs = new DataSourceConfig();
        existingDs.setId(77L);
        existingDs.setType(DataSourceType.GITHUB);
        existingDs.setBaseUrl("https://api.github.com");

        GitRepositoryEntity existingRepo = new GitRepositoryEntity();
        existingRepo.setId(8L);
        existingRepo.setName("owner/already-tracked");
        existingRepo.setRepoFullName("owner/already-tracked");
        existingRepo.setRepoType(RepoType.GITHUB);
        existingRepo.setDataSourceConfig(existingDs);

        when(gitRepoRepository.findByRepoFullName("owner/already-tracked"))
                .thenReturn(Optional.of(existingRepo));
        when(gitRepoRepository.countByDataSourceConfig(existingDs)).thenReturn(1L);

        DataSourceResponseDto result = service.create(USER_ID, req);

        assertThat(result.id()).isEqualTo(77L);
        assertThat(result.repoCount()).isEqualTo(1L);
        verify(gitHubRepositoryService).registerGitHubRepo(USER_ID, 77L, "owner/already-tracked");
        verify(repository, never()).save(any());
    }

    // ── create: buildAndSaveDataSource team branch + GIT_LOCAL auto-register ─

    @Test
    void create_gitLocalWithTeam_registersLocalRepoFails_logsWarningAndStillReturnsDto() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        User manager = new User();
        manager.setId(USER_ID);
        manager.setRole(Role.MANAGER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(manager);

        Team team = new Team();
        team.setId(5L);
        team.setManager(manager);
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));

        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(DataSourceType.GIT_LOCAL);
        req.setName("Team Local Repo");
        req.setPath("/tmp/repo");
        req.setTeamId(5L);

        DataSourceConfig saved = new DataSourceConfig();
        saved.setId(40L);
        saved.setType(DataSourceType.GIT_LOCAL);
        saved.setName("Team Local Repo");
        saved.setPath("/tmp/repo");
        saved.setTeam(team);
        when(repository.save(any())).thenReturn(saved);
        when(gitRepoRepository.countByDataSourceConfig(saved)).thenReturn(0L);

        when(gitRepositoryService.registerLocalRepo(eq(USER_ID), any()))
                .thenThrow(new IllegalArgumentException("Local path is not a directory: /tmp/repo"));

        DataSourceResponseDto result = service.create(USER_ID, req);

        assertThat(result.id()).isEqualTo(40L);
        assertThat(result.teamId()).isEqualTo(5L);
        verify(gitRepositoryService).registerLocalRepo(eq(USER_ID), any());
    }

    // ── create: buildAndSaveDataSource team branch + GITHUB catch ────────────

    @Test
    void create_githubWithTeam_adminManagesTeam_registerRepoFails_logsWarning() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        User admin = new User();
        admin.setId(USER_ID);
        admin.setRole(Role.ADMIN);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(admin);

        Team team = new Team();
        team.setId(6L);
        User otherManager = new User();
        otherManager.setId(99L);
        team.setManager(otherManager);
        when(teamRepository.findById(6L)).thenReturn(Optional.of(team));

        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(DataSourceType.GITHUB);
        req.setName("Team GitHub DS");
        req.setBaseUrl("https://api.github.com");
        req.setApiToken("ghp_test");
        req.setTeamId(6L);
        req.setRepoFullName("owner/team-repo");

        when(gitRepoRepository.findByRepoFullName("owner/team-repo")).thenReturn(Optional.empty());

        DataSourceConfig saved = new DataSourceConfig();
        saved.setId(41L);
        saved.setType(DataSourceType.GITHUB);
        saved.setName("Team GitHub DS");
        saved.setBaseUrl("https://api.github.com");
        saved.setTeam(team);
        when(repository.save(any())).thenReturn(saved);
        when(gitRepoRepository.countByDataSourceConfig(saved)).thenReturn(0L);

        doThrow(new RuntimeException("GitHub API unavailable"))
                .when(gitHubRepositoryService).registerGitHubRepo(USER_ID, 41L, "owner/team-repo");

        DataSourceResponseDto result = service.create(USER_ID, req);

        assertThat(result.id()).isEqualTo(41L);
        assertThat(result.teamId()).isEqualTo(6L);
    }

    // ── create: JIRA auto-register cross-DS canonical project ────────────────

    @Test
    void create_jiraWithTeam_autoCreatedProjectBelongsToDifferentDs_deletesEmptyDsReturnsCanonical() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        User manager = new User();
        manager.setId(USER_ID);
        manager.setRole(Role.MANAGER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(manager);

        Team team = new Team();
        team.setId(7L);
        team.setManager(manager);
        when(teamRepository.findById(7L)).thenReturn(Optional.of(team));

        when(jiraProjectService.findProjectsByBaseUrl("https://work.atlassian.net"))
                .thenReturn(List.of());

        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(DataSourceType.JIRA);
        req.setName("Team Jira DS");
        req.setBaseUrl("https://work.atlassian.net");
        req.setApiToken("user:token");
        req.setTeamId(7L);
        req.setProjectKey("PDA");

        DataSourceConfig saved = new DataSourceConfig();
        saved.setId(50L);
        saved.setType(DataSourceType.JIRA);
        saved.setName("Team Jira DS");
        saved.setBaseUrl("https://work.atlassian.net");
        saved.setTeam(team);
        when(repository.save(any())).thenReturn(saved);

        // Canonical project already exists under a DIFFERENT datasource (id=99).
        DataSourceConfig canonicalDs = new DataSourceConfig();
        canonicalDs.setId(99L);
        canonicalDs.setType(DataSourceType.JIRA);
        canonicalDs.setBaseUrl("https://work.atlassian.net");

        JiraProjectEntity canonicalProject = new JiraProjectEntity();
        canonicalProject.setId(500L);
        canonicalProject.setDataSource(canonicalDs);
        canonicalProject.setProjectKey("PDA");
        canonicalProject.setProjectName("PDA Project");

        when(jiraProjectService.addProject(saved, "PDA", null)).thenReturn(canonicalProject);
        when(gitRepoRepository.countByDataSourceConfig(canonicalDs)).thenReturn(0L);

        DataSourceResponseDto result = service.create(USER_ID, req);

        assertThat(result.id()).isEqualTo(99L);
        verify(jiraProjectService).subscribeUser(500L, USER_ID);
        verify(repository).delete(saved);
    }

    @Test
    void create_jira_autoCreateProjectThrows_logsWarningAndReturnsOwnDto() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        when(jiraProjectService.findProjectsByBaseUrl("https://work.atlassian.net"))
                .thenReturn(List.of());

        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(DataSourceType.JIRA);
        req.setName("Personal Jira DS");
        req.setBaseUrl("https://work.atlassian.net");
        req.setApiToken("user:token");
        req.setProjectKey("PDA");

        DataSourceConfig saved = new DataSourceConfig();
        saved.setId(51L);
        saved.setType(DataSourceType.JIRA);
        saved.setName("Personal Jira DS");
        saved.setBaseUrl("https://work.atlassian.net");
        when(repository.save(any())).thenReturn(saved);
        when(gitRepoRepository.countByDataSourceConfig(saved)).thenReturn(0L);

        when(jiraProjectService.addProject(saved, "PDA", null))
                .thenThrow(new RuntimeException("Jira API unreachable"));

        DataSourceResponseDto result = service.create(USER_ID, req);

        assertThat(result.id()).isEqualTo(51L);
        verify(repository, never()).delete(any());
    }

    // ── listForUser ───────────────────────────────────────────────────────

    @Test
    void listForUser_personalTeamAndSubscribedSources_allBranchesAndDedupExercised() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserRole).thenReturn(Role.DEVELOPER);

        User user = new User();
        user.setId(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        // Personal (non-team) DS.
        DataSourceConfig personalCfg = new DataSourceConfig();
        personalCfg.setId(1L);
        personalCfg.setType(DataSourceType.GIT_LOCAL);
        personalCfg.setName("Personal");
        when(repository.findAllByUserAndTeamIsNull(user)).thenReturn(List.of(personalCfg));
        when(gitRepoRepository.countByDataSourceConfig(personalCfg)).thenReturn(0L);

        // Team A: user is a member but NOT the manager → canDelete=false.
        Team teamA = new Team();
        teamA.setId(10L);
        User otherManager = new User();
        otherManager.setId(99L);
        teamA.setManager(otherManager);
        when(teamRepository.findByMembersId(USER_ID)).thenReturn(List.of(teamA));

        DataSourceConfig teamACfg = new DataSourceConfig();
        teamACfg.setId(2L);
        teamACfg.setType(DataSourceType.GITHUB);
        teamACfg.setName("Team A GitHub");
        teamACfg.setTeam(teamA);
        when(repository.findAllByTeam(teamA)).thenReturn(List.of(teamACfg));
        when(gitRepoRepository.countByDataSourceConfig(teamACfg)).thenReturn(0L);

        // Team B: user IS the manager (returned by findByManagerId only) → canDelete=true.
        Team teamB = new Team();
        teamB.setId(11L);
        teamB.setManager(user);
        when(teamRepository.findByManagerId(USER_ID)).thenReturn(List.of(teamB));

        DataSourceConfig teamBCfg = new DataSourceConfig();
        teamBCfg.setId(3L);
        teamBCfg.setType(DataSourceType.JIRA);
        teamBCfg.setName("Team B Jira");
        teamBCfg.setTeam(teamB);
        when(repository.findAllByTeam(teamB)).thenReturn(List.of(teamBCfg));
        when(gitRepoRepository.countByDataSourceConfig(teamBCfg)).thenReturn(0L);

        // Repo-subscription DSs: one duplicate of personalCfg (dedup skip), one new.
        DataSourceConfig subRepoCfg = new DataSourceConfig();
        subRepoCfg.setId(4L);
        subRepoCfg.setType(DataSourceType.GITHUB);
        subRepoCfg.setName("Subscribed via repo");
        when(userRepoRegRepository.findDataSourceConfigsByUserId(USER_ID))
                .thenReturn(List.of(personalCfg, subRepoCfg));
        when(gitRepoRepository.countByDataSourceConfig(subRepoCfg)).thenReturn(0L);

        // Jira-subscription DSs: one duplicate of subRepoCfg (dedup skip), one new.
        DataSourceConfig subJiraCfg = new DataSourceConfig();
        subJiraCfg.setId(5L);
        subJiraCfg.setType(DataSourceType.JIRA);
        subJiraCfg.setName("Subscribed via Jira");
        when(jiraProjectService.findSubscribedDataSourceConfigs(USER_ID))
                .thenReturn(List.of(subRepoCfg, subJiraCfg));
        when(gitRepoRepository.countByDataSourceConfig(subJiraCfg)).thenReturn(0L);

        List<DataSourceResponseDto> result = service.listForUser(USER_ID);

        assertThat(result).extracting(DataSourceResponseDto::id)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        // personalCfg → canDelete true (creator)
        assertThat(result.get(0).canDelete()).isTrue();
        // teamACfg → user is member, not manager, not admin → canDelete false
        assertThat(result.get(1).canDelete()).isFalse();
        // teamBCfg → user is manager → canDelete true
        assertThat(result.get(2).canDelete()).isTrue();
        // subscribed entries are always read-only
        assertThat(result.get(3).canDelete()).isFalse();
        assertThat(result.get(4).canDelete()).isFalse();
    }

    @Test
    void listForUser_sameTeamReturnedAsMemberAndManager_adminRole_dedupSkipsSecondPass() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserRole).thenReturn(Role.ADMIN);

        User user = new User();
        user.setId(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        when(repository.findAllByUserAndTeamIsNull(user)).thenReturn(List.of());

        Team team = new Team();
        team.setId(20L);
        team.setManager(user);

        // Same team object returned by BOTH queries → second pass must be skipped via addedTeamIds.
        when(teamRepository.findByMembersId(USER_ID)).thenReturn(List.of(team));
        when(teamRepository.findByManagerId(USER_ID)).thenReturn(List.of(team));

        DataSourceConfig teamCfg = new DataSourceConfig();
        teamCfg.setId(30L);
        teamCfg.setType(DataSourceType.GITHUB);
        teamCfg.setName("Shared Team DS");
        teamCfg.setTeam(team);
        when(repository.findAllByTeam(team)).thenReturn(List.of(teamCfg));
        when(gitRepoRepository.countByDataSourceConfig(teamCfg)).thenReturn(0L);

        when(userRepoRegRepository.findDataSourceConfigsByUserId(USER_ID)).thenReturn(List.of());
        when(jiraProjectService.findSubscribedDataSourceConfigs(USER_ID)).thenReturn(List.of());

        List<DataSourceResponseDto> result = service.listForUser(USER_ID);

        // Only ONE entry for the shared team — the manager-loop pass was skipped by dedup.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(30L);
        // ADMIN role → canDelete true even via the member-loop branch.
        assertThat(result.get(0).canDelete()).isTrue();
        verify(repository, times(1)).findAllByTeam(team);
    }

    // ── getForUser ────────────────────────────────────────────────────────

    @Test
    void getForUser_ownedByUser_returnsDirectly() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        DataSourceConfig owned = new DataSourceConfig();
        owned.setId(100L);
        when(repository.findByIdAndUser(100L, user)).thenReturn(Optional.of(owned));

        DataSourceConfig result = service.getForUser(USER_ID, 100L);

        assertThat(result.getId()).isEqualTo(100L);
        verify(repository, never()).findById(100L);
    }

    @Test
    void getForUser_notFoundInRepositoryAtAll_throwsNoSuchElement() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        when(repository.findByIdAndUser(404L, user)).thenReturn(Optional.empty());
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForUser(USER_ID, 404L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getForUser_teamScoped_userIsAdmin_returnsViaCanAccessTeam() {
        User admin = new User();
        admin.setId(USER_ID);
        admin.setRole(Role.ADMIN);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(admin);

        when(repository.findByIdAndUser(200L, admin)).thenReturn(Optional.empty());

        Team team = new Team();
        team.setId(60L);
        User otherManager = new User();
        otherManager.setId(99L);
        team.setManager(otherManager);

        DataSourceConfig teamCfg = new DataSourceConfig();
        teamCfg.setId(200L);
        teamCfg.setTeam(team);
        when(repository.findById(200L)).thenReturn(Optional.of(teamCfg));

        DataSourceConfig result = service.getForUser(USER_ID, 200L);

        assertThat(result.getId()).isEqualTo(200L);
    }

    @Test
    void getForUser_teamScoped_userIsTeamManager_returnsViaCanAccessTeam() {
        User manager = new User();
        manager.setId(USER_ID);
        manager.setRole(Role.MANAGER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(manager);

        when(repository.findByIdAndUser(201L, manager)).thenReturn(Optional.empty());

        Team team = new Team();
        team.setId(61L);
        team.setManager(manager);

        DataSourceConfig teamCfg = new DataSourceConfig();
        teamCfg.setId(201L);
        teamCfg.setTeam(team);
        when(repository.findById(201L)).thenReturn(Optional.of(teamCfg));

        DataSourceConfig result = service.getForUser(USER_ID, 201L);

        assertThat(result.getId()).isEqualTo(201L);
    }

    @Test
    void getForUser_teamScoped_userIsPlainMember_returnsViaCanAccessTeam() {
        User member = new User();
        member.setId(USER_ID);
        member.setRole(Role.DEVELOPER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(member);

        when(repository.findByIdAndUser(202L, member)).thenReturn(Optional.empty());

        Team team = new Team();
        team.setId(62L);
        User otherManager = new User();
        otherManager.setId(99L);
        team.setManager(otherManager);

        DataSourceConfig teamCfg = new DataSourceConfig();
        teamCfg.setId(202L);
        teamCfg.setTeam(team);
        when(repository.findById(202L)).thenReturn(Optional.of(teamCfg));
        when(teamRepository.existsByIdAndMembersId(62L, USER_ID)).thenReturn(true);

        DataSourceConfig result = service.getForUser(USER_ID, 202L);

        assertThat(result.getId()).isEqualTo(202L);
    }

    @Test
    void getForUser_subscribedViaRepoRegistration_returnsTeamCfg() {
        User user = new User();
        user.setId(USER_ID);
        user.setRole(Role.DEVELOPER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        when(repository.findByIdAndUser(203L, user)).thenReturn(Optional.empty());

        // No team at all — skips canAccessTeam entirely.
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(203L);
        when(repository.findById(203L)).thenReturn(Optional.of(cfg));

        when(userRepoRegRepository.existsByUserIdAndDataSourceConfig_Id(USER_ID, 203L)).thenReturn(true);

        DataSourceConfig result = service.getForUser(USER_ID, 203L);

        assertThat(result.getId()).isEqualTo(203L);
    }

    @Test
    void getForUser_subscribedViaJiraProject_returnsTeamCfg() {
        User user = new User();
        user.setId(USER_ID);
        user.setRole(Role.DEVELOPER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        when(repository.findByIdAndUser(204L, user)).thenReturn(Optional.empty());

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(204L);
        when(repository.findById(204L)).thenReturn(Optional.of(cfg));

        when(userRepoRegRepository.existsByUserIdAndDataSourceConfig_Id(USER_ID, 204L)).thenReturn(false);
        when(jiraProjectService.hasSubscriptionForDataSource(USER_ID, 204L)).thenReturn(true);

        DataSourceConfig result = service.getForUser(USER_ID, 204L);

        assertThat(result.getId()).isEqualTo(204L);
    }

    @Test
    void getForUser_existsButNoAccessAtAll_throwsNoSuchElement() {
        User user = new User();
        user.setId(USER_ID);
        user.setRole(Role.DEVELOPER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        when(repository.findByIdAndUser(205L, user)).thenReturn(Optional.empty());

        // teamCfg has no team (null) → canAccessTeam not even attempted.
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(205L);
        when(repository.findById(205L)).thenReturn(Optional.of(cfg));

        when(userRepoRegRepository.existsByUserIdAndDataSourceConfig_Id(USER_ID, 205L)).thenReturn(false);
        when(jiraProjectService.hasSubscriptionForDataSource(USER_ID, 205L)).thenReturn(false);

        assertThatThrownBy(() -> service.getForUser(USER_ID, 205L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("205");
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    void update_ownedConfig_allFieldsProvided_updatesAndSaves() {
        User owner = new User();
        owner.setId(USER_ID);

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(300L);
        cfg.setType(DataSourceType.GITHUB);
        cfg.setName("Old Name");
        cfg.setBaseUrl("https://api.github.com");
        cfg.setPath(null);
        cfg.setEnabled(false);
        cfg.setUser(owner);
        when(repository.findById(300L)).thenReturn(Optional.of(cfg));

        UpdateDataSourceRequest req = new UpdateDataSourceRequest();
        req.setName("New Name");
        req.setBaseUrl("https://github.example.com");
        req.setPath("/new/path");
        req.setApiToken("new-token");
        req.setEnabled(true);

        when(tokenEncryptor.encrypt("new-token")).thenReturn("encrypted-new-token");
        when(repository.save(cfg)).thenReturn(cfg);

        DataSourceConfig result = service.update(USER_ID, 300L, req);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getBaseUrl()).isEqualTo("https://github.example.com");
        assertThat(result.getPath()).isEqualTo("/new/path");
        assertThat(result.getApiTokenEncrypted()).isEqualTo("encrypted-new-token");
        assertThat(result.isEnabled()).isTrue();
        verify(repository).save(cfg);
    }

    @Test
    void update_dsNotFound_throwsNoSuchElement() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, 999L, new UpdateDataSourceRequest()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("999");
    }

    @Test
    void update_teamScoped_managerOfTeam_canUpdate() {
        User manager = new User();
        manager.setId(USER_ID);
        manager.setRole(Role.MANAGER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(manager);

        Team team = new Team();
        team.setId(70L);
        team.setManager(manager);

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(301L);
        cfg.setType(DataSourceType.GITHUB);
        cfg.setName("Team DS");
        cfg.setTeam(team);
        // No owning user — team-scoped only.
        when(repository.findById(301L)).thenReturn(Optional.of(cfg));
        when(repository.save(cfg)).thenReturn(cfg);

        UpdateDataSourceRequest req = new UpdateDataSourceRequest();
        req.setName("Renamed Team DS");

        DataSourceConfig result = service.update(USER_ID, 301L, req);

        assertThat(result.getName()).isEqualTo("Renamed Team DS");
    }

    @Test
    void update_teamScoped_managerOfDifferentTeam_throwsForbidden() {
        User manager = new User();
        manager.setId(USER_ID);
        manager.setRole(Role.MANAGER);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(manager);

        Team team = new Team();
        team.setId(71L);
        User otherManager = new User();
        otherManager.setId(99L);
        team.setManager(otherManager);

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(302L);
        cfg.setType(DataSourceType.GITHUB);
        cfg.setTeam(team);
        when(repository.findById(302L)).thenReturn(Optional.of(cfg));

        assertThatThrownBy(() -> service.update(USER_ID, 302L, new UpdateDataSourceRequest()))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_neitherOwnerNorTeamScoped_throwsForbidden() {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(303L);
        cfg.setType(DataSourceType.GITHUB);
        // No user, no team.
        when(repository.findById(303L)).thenReturn(Optional.of(cfg));

        assertThatThrownBy(() -> service.update(USER_ID, 303L, new UpdateDataSourceRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_ownedConfig_deletes() {
        User owner = new User();
        owner.setId(USER_ID);

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(310L);
        cfg.setUser(owner);
        when(repository.findById(310L)).thenReturn(Optional.of(cfg));

        service.delete(USER_ID, 310L);

        verify(repository).delete(cfg);
    }

    // ── listReposForDataSource / toRepoDtoWithSubscribed / githubWebUrl ─────

    @Test
    void listReposForDataSource_githubApiBaseUrl_buildsGithubComRepoUrl() {
        User owner = new User();
        owner.setId(USER_ID);

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(400L);
        cfg.setType(DataSourceType.GITHUB);
        cfg.setBaseUrl("https://api.github.com");
        cfg.setUser(owner);
        when(repository.findByIdAndUser(400L, owner)).thenReturn(Optional.of(cfg));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(50L);
        repo.setName("owner/repo");
        repo.setRepoFullName("owner/repo");
        repo.setRepoType(RepoType.GITHUB);
        repo.setDataSourceConfig(cfg);

        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of(50L));

        List<RepoDto> result = service.listReposForDataSource(USER_ID, 400L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).repoUrl()).isEqualTo("https://github.com/owner/repo");
        assertThat(result.get(0).subscribed()).isTrue();
        assertThat(result.get(0).teamId()).isNull();
    }

    @Test
    void listReposForDataSource_githubEnterpriseBaseUrl_stripsApiV3Suffix() {
        User owner = new User();
        owner.setId(USER_ID);

        Team team = new Team();
        team.setId(80L);

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(401L);
        cfg.setType(DataSourceType.GITHUB);
        cfg.setBaseUrl("https://github.mycompany.com/api/v3");
        cfg.setUser(owner);
        cfg.setTeam(team);
        when(repository.findByIdAndUser(401L, owner)).thenReturn(Optional.of(cfg));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(51L);
        repo.setName("owner/enterprise-repo");
        repo.setRepoFullName("owner/enterprise-repo");
        repo.setRepoType(RepoType.GITHUB);
        repo.setDataSourceConfig(cfg);

        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of());

        List<RepoDto> result = service.listReposForDataSource(USER_ID, 401L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).repoUrl()).isEqualTo("https://github.mycompany.com/owner/enterprise-repo");
        assertThat(result.get(0).subscribed()).isFalse();
        assertThat(result.get(0).teamId()).isEqualTo(80L);
    }

    @Test
    void listReposForDataSource_localRepo_noRepoUrl() {
        User owner = new User();
        owner.setId(USER_ID);

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(402L);
        cfg.setType(DataSourceType.GIT_LOCAL);
        cfg.setUser(owner);
        when(repository.findByIdAndUser(402L, owner)).thenReturn(Optional.of(cfg));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(52L);
        repo.setName("local-repo");
        repo.setLocalPath("/home/user/local-repo");
        repo.setRepoType(RepoType.LOCAL);
        repo.setDataSourceConfig(cfg);

        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(userRepoRegRepository.findRepoIdsByUserId(USER_ID)).thenReturn(List.of());

        List<RepoDto> result = service.listReposForDataSource(USER_ID, 402L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).repoUrl()).isNull();
        assertThat(result.get(0).dataSourceId()).isEqualTo(402L);
    }
}
