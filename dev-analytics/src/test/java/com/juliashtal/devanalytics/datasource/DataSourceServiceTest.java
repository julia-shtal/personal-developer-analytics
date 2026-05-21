package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.model.dto.DataSourceResponseDto;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.DataSourceValidator;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.git.service.GitRepositoryService;
import com.juliashtal.devanalytics.github.service.GitHubRepositoryService;
import com.juliashtal.devanalytics.jira.JiraProjectService;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceTest {

    @Mock DataSourceConfigRepository repository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock SimpleTokenEncryptor tokenEncryptor;
    @Mock DataSourceValidator validator;
    @Mock GitRepositoryService gitRepositoryService;
    @Mock GitHubRepositoryService gitHubRepositoryService;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;
    @Mock JiraProjectService jiraProjectService;

    @InjectMocks DataSourceService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    // ── T2.1: GITHUB without repoFullName creates DS with repoCount = 0 ───────

    @Test
    void createGithubDataSource_withoutRepo_succeeds() {
        DataSourceConfig saved = savedConfig(DataSourceType.GITHUB, 10L);
        when(repository.save(any())).thenReturn(saved);
        when(gitRepoRepository.countByDataSourceConfig(saved)).thenReturn(0L);

        DataSourceResponseDto result = service.create(1L, githubReq(null));

        assertThat(result.repoCount()).isZero();
        assertThat(result.id()).isEqualTo(10L);
        verify(gitHubRepositoryService, never()).registerGitHubRepo(any(), any(), any());
    }

    @Test
    void createGithubDataSource_withRepo_attachesRepo() {
        when(gitRepoRepository.findByRepoFullName("owner/repo")).thenReturn(Optional.empty());
        DataSourceConfig saved = savedConfig(DataSourceType.GITHUB, 11L);
        when(repository.save(any())).thenReturn(saved);
        when(gitRepoRepository.countByDataSourceConfig(saved)).thenReturn(1L);

        DataSourceResponseDto result = service.create(1L, githubReq("owner/repo"));

        assertThat(result.repoCount()).isEqualTo(1L);
        verify(gitHubRepositoryService).registerGitHubRepo(1L, 11L, "owner/repo");
    }

    // ── Jira: subscribe-instead-of-duplicate ─────────────────────────────────

    @Test
    void createJiraDs_existingBaseUrl_withProjectKey_subscribesToSpecificProject() {
        DataSourceConfig canonicalDs = savedJiraConfig(99L, "https://work.atlassian.net");
        JiraProjectEntity pda = jiraProject(canonicalDs, 200L, "PDA");

        when(jiraProjectService.findProjectsByBaseUrl("https://work.atlassian.net"))
                .thenReturn(List.of(pda));
        when(gitRepoRepository.countByDataSourceConfig(canonicalDs)).thenReturn(0L);

        DataSourceResponseDto result = service.create(1L, jiraReq("PDA"));

        assertThat(result.id()).isEqualTo(99L);
        verify(jiraProjectService).subscribeUser(200L, 1L);
        verify(repository, never()).save(any());  // no new DS created
    }

    @Test
    void createJiraDs_existingBaseUrl_noProjectKey_subscribesToAllProjects() {
        DataSourceConfig canonicalDs = savedJiraConfig(99L, "https://work.atlassian.net");
        JiraProjectEntity pda  = jiraProject(canonicalDs, 200L, "PDA");
        JiraProjectEntity tfta = jiraProject(canonicalDs, 201L, "TFTA");

        when(jiraProjectService.findProjectsByBaseUrl("https://work.atlassian.net"))
                .thenReturn(List.of(pda, tfta));
        when(gitRepoRepository.countByDataSourceConfig(canonicalDs)).thenReturn(0L);

        DataSourceResponseDto result = service.create(1L, jiraReq(null));

        assertThat(result.id()).isEqualTo(99L);
        verify(jiraProjectService).subscribeUser(200L, 1L);
        verify(jiraProjectService).subscribeUser(201L, 1L);
        verify(repository, never()).save(any());
    }

    @Test
    void createJiraDs_existingBaseUrl_requestedKeyNotTracked_subscribesToAllFallback() {
        DataSourceConfig canonicalDs = savedJiraConfig(99L, "https://work.atlassian.net");
        JiraProjectEntity pda = jiraProject(canonicalDs, 200L, "PDA");

        when(jiraProjectService.findProjectsByBaseUrl("https://work.atlassian.net"))
                .thenReturn(List.of(pda));
        when(gitRepoRepository.countByDataSourceConfig(canonicalDs)).thenReturn(0L);

        // TFTA is not tracked yet under the existing DS.
        DataSourceResponseDto result = service.create(1L, jiraReq("TFTA"));

        assertThat(result.id()).isEqualTo(99L);
        // Falls back to subscribing all existing projects.
        verify(jiraProjectService).subscribeUser(200L, 1L);
        verify(repository, never()).save(any());
    }

    @Test
    void createJiraDs_noExistingBaseUrl_createsNewDs() {
        when(jiraProjectService.findProjectsByBaseUrl("https://work.atlassian.net"))
                .thenReturn(List.of());

        DataSourceConfig saved = savedJiraConfig(60L, "https://work.atlassian.net");
        when(repository.save(any())).thenReturn(saved);

        JiraProjectEntity ownProject = jiraProject(saved, 300L, "PDA");
        when(jiraProjectService.addProject(any(), eq("PDA"), any())).thenReturn(ownProject);
        when(gitRepoRepository.countByDataSourceConfig(saved)).thenReturn(0L);

        DataSourceResponseDto result = service.create(1L, jiraReq("PDA"));

        assertThat(result.id()).isEqualTo(60L);
        verify(repository).save(any());
    }

    // ── repoCount reflects real count at time of DTO construction ─────────────

    @Test
    void repoCount_returnedByToDto_matchesRepository() {
        DataSourceConfig saved = savedConfig(DataSourceType.GITHUB, 12L);
        when(repository.save(any())).thenReturn(saved);
        when(gitRepoRepository.countByDataSourceConfig(saved)).thenReturn(3L);

        DataSourceResponseDto result = service.create(1L, githubReq(null));

        assertThat(result.repoCount()).isEqualTo(3L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static CreateDataSourceRequest githubReq(String repoFullName) {
        CreateDataSourceRequest r = new CreateDataSourceRequest();
        r.setType(DataSourceType.GITHUB);
        r.setName("Work GitHub");
        r.setBaseUrl("https://api.github.com");
        r.setApiToken("ghp_test");
        r.setRepoFullName(repoFullName);
        return r;
    }

    private static DataSourceConfig savedConfig(DataSourceType type, Long id) {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(id);
        cfg.setType(type);
        cfg.setName("Work GitHub");
        cfg.setBaseUrl("https://api.github.com");
        cfg.setEnabled(true);
        return cfg;
    }

    private static CreateDataSourceRequest jiraReq(String projectKey) {
        CreateDataSourceRequest r = new CreateDataSourceRequest();
        r.setType(DataSourceType.JIRA);
        r.setName("Work Jira");
        r.setBaseUrl("https://work.atlassian.net");
        r.setApiToken("user:token");
        r.setProjectKey(projectKey);
        return r;
    }

    private static DataSourceConfig savedJiraConfig(Long id, String baseUrl) {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(id);
        cfg.setType(DataSourceType.JIRA);
        cfg.setName("Work Jira");
        cfg.setBaseUrl(baseUrl);
        cfg.setEnabled(true);
        return cfg;
    }

    private static JiraProjectEntity jiraProject(DataSourceConfig ds, Long id, String key) {
        JiraProjectEntity p = new JiraProjectEntity();
        p.setId(id);
        p.setDataSource(ds);
        p.setProjectKey(key);
        p.setProjectName(key + " Project");
        return p;
    }
}