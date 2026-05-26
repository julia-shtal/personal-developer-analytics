package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepoRegistrationTest {

    @Mock UserRepoRegistrationRepository userRepoRegRepository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock DataSourceConfigRepository dataSourceConfigRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock
    JiraProjectService jiraProjectService;
    @Mock com.juliashtal.devanalytics.datasource.service.DataSourceValidator validator;
    @Mock com.juliashtal.devanalytics.git.service.GitRepositoryService gitRepositoryService;
    @Mock com.juliashtal.devanalytics.github.service.GitHubRepositoryService gitHubRepositoryService;
    @Mock com.juliashtal.devanalytics.security.SimpleTokenEncryptor tokenEncryptor;

    @InjectMocks DataSourceService dataSourceService;

    // ── Entity structure ──────────────────────────────────────────────────────

    @Test
    void userRepoRegistration_hasNoDataSourceConfigField() {
        boolean hasField = Arrays.stream(UserRepoRegistration.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("dataSourceConfig"));
        assertThat(hasField)
                .as("dataSourceConfig column was dropped by V34 — field must not exist")
                .isFalse();
    }

    @Test
    void userRepoRegistration_hasExactlyThreeColumns() {
        // id, user, repository — dataSourceConfig was removed
        long mappedFields = Arrays.stream(UserRepoRegistration.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .count();
        assertThat(mappedFields).isEqualTo(3);
    }

    // ── DataSourceService.create subscribes to existing Jira project instead of duplicating ──

    @Test
    void create_jiraWithExistingBaseUrl_subscribesAndReturnsExistingDs() {
        long userId = 1L;
        com.juliashtal.devanalytics.datasource.model.DataSourceConfig existingDs =
                new com.juliashtal.devanalytics.datasource.model.DataSourceConfig();
        existingDs.setId(99L);
        existingDs.setType(com.juliashtal.devanalytics.datasource.model.DataSourceType.JIRA);
        existingDs.setName("User1 Jira");
        existingDs.setBaseUrl("https://work.atlassian.net");

        com.juliashtal.devanalytics.jira.model.JiraProjectEntity existingProject =
                new com.juliashtal.devanalytics.jira.model.JiraProjectEntity();
        existingProject.setId(5L);
        existingProject.setProjectKey("PDA");
        existingProject.setDataSource(existingDs);

        var req = new com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest();
        req.setType(com.juliashtal.devanalytics.datasource.model.DataSourceType.JIRA);
        req.setName("Work Jira");
        req.setBaseUrl("https://work.atlassian.net");
        req.setProjectKey("PDA");
        req.setApiToken("user:token");

        // New pre-check: looks up by baseUrl (not just by projectKey).
        when(jiraProjectService.findProjectsByBaseUrl("https://work.atlassian.net"))
                .thenReturn(java.util.List.of(existingProject));
        when(userRepository.findById(userId))
                .thenReturn(java.util.Optional.of(new com.juliashtal.devanalytics.user.model.User()));
        when(gitRepoRepository.countByDataSourceConfig(existingDs)).thenReturn(0L);

        com.juliashtal.devanalytics.datasource.model.dto.DataSourceResponseDto result =
                dataSourceService.create(userId, req);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.canDelete()).isFalse();
        verify(jiraProjectService).subscribeUser(5L, userId);
        verify(dataSourceConfigRepository, never()).save(any());
    }

    // ── DataSourceService.getForUser uses the rerouted existsByUserIdAndDataSourceConfig_Id ──

    @Test
    void getForUser_allowsSubscribedUser_viaRepoPath() {
        long userId = 1L;
        long dsId = 10L;

        DataSourceConfig ds = new DataSourceConfig();
        ds.setId(dsId);

        when(dataSourceConfigRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(dataSourceConfigRepository.findByIdAndUser(eq(dsId), any())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(new com.juliashtal.devanalytics.user.model.User());
        // Simulate: user subscribed to a repo under this DS (query joins via repository.dataSourceConfig)
        when(userRepoRegRepository.existsByUserIdAndDataSourceConfig_Id(userId, dsId)).thenReturn(true);

        DataSourceConfig result = dataSourceService.getForUser(userId, dsId);

        assertThat(result.getId()).isEqualTo(dsId);
        verify(userRepoRegRepository).existsByUserIdAndDataSourceConfig_Id(userId, dsId);
    }
}
