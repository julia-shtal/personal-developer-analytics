package com.juliashtal.devanalytics.jira;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.UserProjectRegistration;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepository;
import com.juliashtal.devanalytics.jira.repository.UserProjectRegistrationRepository;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraProjectServiceTest {

    @Mock
    JiraProjectRepository jiraProjectRepository;
    @Mock
    UserProjectRegistrationRepository userProjectRegistrationRepository;
    @Mock UserRepository userRepository;
    @Mock RestTemplate restTemplate;
    @Mock TokenEncryptor tokenEncryptor;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    JiraProjectService service;

    private DataSourceConfig ds;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);

        ds = new DataSourceConfig();
        ds.setId(10L);
        ds.setUser(owner);
        ds.setBaseUrl("https://work.atlassian.net");
    }

    @Test
    void addProject_newKey_createsAndReturns() {
        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(
                "https://work.atlassian.net", "PROJ")).thenReturn(Optional.empty());
        when(jiraProjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JiraProjectEntity result = service.addProject(ds, "proj", "My Project");

        assertThat(result.getProjectKey()).isEqualTo("PROJ");
        assertThat(result.getProjectName()).isEqualTo("My Project");
        verify(jiraProjectRepository).save(any());
    }

    @Test
    void addProject_existingKeySameDs_returnsExisting() {
        JiraProjectEntity existing = new JiraProjectEntity();
        existing.setProjectKey("PROJ");
        existing.setDataSource(ds);
        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(
                "https://work.atlassian.net", "PROJ")).thenReturn(Optional.of(existing));

        JiraProjectEntity result = service.addProject(ds, "PROJ", null);

        assertThat(result).isSameAs(existing);
        verify(jiraProjectRepository, never()).save(any());
    }

    @Test
    void listTrackedProjects_delegatesToRepository() {
        JiraProjectEntity p1 = new JiraProjectEntity();
        JiraProjectEntity p2 = new JiraProjectEntity();
        when(jiraProjectRepository.findAllByDataSource(ds)).thenReturn(List.of(p1, p2));

        List<JiraProjectEntity> result = service.listTrackedProjects(ds);

        assertThat(result).containsExactly(p1, p2);
    }

    @Test
    void deleteProject_ownerCanDelete() {
        JiraProjectEntity project = new JiraProjectEntity();
        project.setId(5L);
        project.setDataSource(ds);
        when(jiraProjectRepository.findById(5L)).thenReturn(Optional.of(project));

        service.deleteProject(5L, owner.getId());

        verify(jiraProjectRepository).delete(project);
    }

    @Test
    void deleteProject_nonOwnerThrowsForbidden() {
        DataSourceConfig otherDs = new DataSourceConfig();
        User otherOwner = new User();
        otherOwner.setId(99L);
        otherDs.setUser(otherOwner);

        JiraProjectEntity project = new JiraProjectEntity();
        project.setId(5L);
        project.setDataSource(otherDs);
        when(jiraProjectRepository.findById(5L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.deleteProject(5L, owner.getId()))
                .isInstanceOf(ForbiddenException.class);
        verify(jiraProjectRepository, never()).delete(any());
    }

    @Test
    void deleteProject_notFound_throws() {
        when(jiraProjectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProject(99L, 1L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void subscribeUser_idempotent_doesNotDuplicate() {
        JiraProjectEntity project = new JiraProjectEntity();
        project.setId(5L);
        User user = new User();
        user.setId(2L);

        when(jiraProjectRepository.findById(5L)).thenReturn(Optional.of(project));
        when(userRepository.getReferenceById(2L)).thenReturn(user);
        when(userProjectRegistrationRepository.existsByUserAndProject(user, project)).thenReturn(true);

        service.subscribeUser(5L, 2L);

        verify(userProjectRegistrationRepository, never()).save(any());
    }

    @Test
    void findByBaseUrlAndProjectKey_normalizesUrlBeforeLookup() {
        JiraProjectEntity p = new JiraProjectEntity();
        p.setProjectKey("PDA");
        // Service must normalize the URL (lowercase, strip trailing slash) before querying.
        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(
                "https://work.atlassian.net", "PDA")).thenReturn(java.util.Optional.of(p));

        var result = service.findByBaseUrlAndProjectKey("https://work.atlassian.net/", "pda");

        assertThat(result).isPresent();
        assertThat(result.get().getProjectKey()).isEqualTo("PDA");
    }

    @Test
    void subscribeUser_newSubscription_saves() {
        JiraProjectEntity project = new JiraProjectEntity();
        project.setId(5L);
        User user = new User();
        user.setId(2L);

        when(jiraProjectRepository.findById(5L)).thenReturn(Optional.of(project));
        when(userRepository.getReferenceById(2L)).thenReturn(user);
        when(userProjectRegistrationRepository.existsByUserAndProject(user, project)).thenReturn(false);

        service.subscribeUser(5L, 2L);

        verify(userProjectRegistrationRepository).save(any(UserProjectRegistration.class));
    }
}