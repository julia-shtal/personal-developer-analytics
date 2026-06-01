package com.juliashtal.devanalytics.jira;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.UserProjectRegistration;
import com.juliashtal.devanalytics.jira.model.dto.JiraProjectResponseDto;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraProjectAttachDetachTest {

    @Mock DataSourceConfigRepository dataSourceConfigRepository;
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

    private static final Long USER_ID = 1L;
    private static final Long DS_ID = 10L;
    private static final Long PROJECT_ID = 100L;

    private DataSourceConfig jiraDs;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(USER_ID);

        jiraDs = new DataSourceConfig();
        jiraDs.setId(DS_ID);
        jiraDs.setType(DataSourceType.JIRA);
        jiraDs.setUser(owner);
        jiraDs.setBaseUrl("https://mycompany.atlassian.net");

        when(dataSourceConfigRepository.findById(DS_ID)).thenReturn(Optional.of(jiraDs));
    }

    // ── listProjectsForDataSource ─────────────────────────────────────────────

    @Test
    void listProjectsForDataSource_ownerSeesProjects() {
        JiraProjectEntity p = project("KEY");
        when(jiraProjectRepository.findAllByDataSource(jiraDs)).thenReturn(List.of(p));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(userProjectRegistrationRepository.existsByUserAndProject(owner, p)).thenReturn(true);

        List<JiraProjectResponseDto> result = service.listProjectsForDataSource(USER_ID, DS_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).projectKey()).isEqualTo("KEY");
        assertThat(result.get(0).subscribed()).isTrue();
    }

    @Test
    void listProjectsForDataSource_subscriberCanList() {
        User subscriber = new User();
        subscriber.setId(99L);
        when(dataSourceConfigRepository.findById(DS_ID)).thenReturn(Optional.of(jiraDs));
        when(userProjectRegistrationRepository.existsByUserIdAndDataSourceId(99L, DS_ID)).thenReturn(true);
        when(jiraProjectRepository.findAllByDataSource(jiraDs)).thenReturn(List.of());
        when(userRepository.getReferenceById(99L)).thenReturn(subscriber);

        assertThatNoException().isThrownBy(() -> service.listProjectsForDataSource(99L, DS_ID));
    }

    @Test
    void listProjectsForDataSource_strangerThrowsForbidden() {
        when(userProjectRegistrationRepository.existsByUserIdAndDataSourceId(99L, DS_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.listProjectsForDataSource(99L, DS_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── attachProject ─────────────────────────────────────────────────────────

    @Test
    void attachProject_newProject_createsAndSubscribes() {
        JiraProjectEntity saved = project("PROJ");
        saved.setId(PROJECT_ID);
        // Global canonical lookup finds nothing → create path.
        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(
                "https://mycompany.atlassian.net", "PROJ")).thenReturn(Optional.empty());
        when(jiraProjectRepository.save(any())).thenReturn(saved);
        // subscribeUser re-fetches by id
        when(jiraProjectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(saved));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(userProjectRegistrationRepository.existsByUserAndProject(owner, saved)).thenReturn(false);

        JiraProjectResponseDto dto = service.attachProject(USER_ID, DS_ID, "proj", null);

        assertThat(dto.projectKey()).isEqualTo("PROJ");
        assertThat(dto.subscribed()).isTrue();
        verify(userProjectRegistrationRepository).save(any(UserProjectRegistration.class));
        verify(dataSourceConfigRepository, never()).delete(any());
    }

    @Test
    void attachProject_idempotent_returnsExisting() {
        JiraProjectEntity existing = project("PROJ");
        existing.setId(PROJECT_ID);
        // Global canonical lookup finds the same-DS row → idempotent path.
        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(
                "https://mycompany.atlassian.net", "PROJ")).thenReturn(Optional.of(existing));
        // subscribeUser re-fetches by id
        when(jiraProjectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(userProjectRegistrationRepository.existsByUserAndProject(owner, existing)).thenReturn(true);

        JiraProjectResponseDto dto = service.attachProject(USER_ID, DS_ID, "PROJ", null);

        assertThat(dto.projectKey()).isEqualTo("PROJ");
        verify(jiraProjectRepository, never()).save(any());
        verify(dataSourceConfigRepository, never()).delete(any());
    }

    @Test
    void attachProject_crossDs_emptyCallingDs_deletesOrphanAndSubscribes() {
        DataSourceConfig canonicalDs = new DataSourceConfig();
        canonicalDs.setId(999L);
        canonicalDs.setType(DataSourceType.JIRA);
        User canonicalOwner = new User();
        canonicalOwner.setId(77L);
        canonicalDs.setUser(canonicalOwner);

        JiraProjectEntity canonical = new JiraProjectEntity();
        canonical.setId(PROJECT_ID);
        canonical.setDataSource(canonicalDs);  // belongs to a DIFFERENT DS
        canonical.setProjectKey("PROJ");
        canonical.setProjectName("Project PROJ");

        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(
                "https://mycompany.atlassian.net", "PROJ")).thenReturn(Optional.of(canonical));
        when(jiraProjectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(canonical));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(userProjectRegistrationRepository.existsByUserAndProject(owner, canonical)).thenReturn(false);
        // Calling DS is empty — should be deleted.
        when(jiraProjectRepository.findAllByDataSource(jiraDs)).thenReturn(List.of());

        JiraProjectResponseDto dto = service.attachProject(USER_ID, DS_ID, "PROJ", null);

        assertThat(dto.projectKey()).isEqualTo("PROJ");
        assertThat(dto.dataSourceId()).isEqualTo(999L);
        verify(userProjectRegistrationRepository).save(any(UserProjectRegistration.class));
        verify(dataSourceConfigRepository).delete(jiraDs);
    }

    @Test
    void attachProject_crossDs_nonEmptyCallingDs_keepsCallingDs() {
        DataSourceConfig canonicalDs = new DataSourceConfig();
        canonicalDs.setId(999L);
        canonicalDs.setType(DataSourceType.JIRA);
        User canonicalOwner = new User();
        canonicalOwner.setId(77L);
        canonicalDs.setUser(canonicalOwner);

        JiraProjectEntity canonical = new JiraProjectEntity();
        canonical.setId(PROJECT_ID);
        canonical.setDataSource(canonicalDs);
        canonical.setProjectKey("PROJ");

        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(
                "https://mycompany.atlassian.net", "PROJ")).thenReturn(Optional.of(canonical));
        when(jiraProjectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(canonical));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(userProjectRegistrationRepository.existsByUserAndProject(owner, canonical)).thenReturn(false);
        // Calling DS has other projects → do NOT delete it.
        JiraProjectEntity otherProject = project("OTHER");
        when(jiraProjectRepository.findAllByDataSource(jiraDs)).thenReturn(List.of(otherProject));

        service.attachProject(USER_ID, DS_ID, "PROJ", null);

        verify(dataSourceConfigRepository, never()).delete(any());
    }

    @Test
    void attachProject_nonOwner_throwsForbidden() {
        assertThatThrownBy(() -> service.attachProject(99L, DS_ID, "PROJ", null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void attachProject_nonJiraDs_throwsBadRequest() {
        jiraDs.setType(DataSourceType.GITHUB);

        assertThatThrownBy(() -> service.attachProject(USER_ID, DS_ID, "PROJ", null))
                .isInstanceOf(BadRequestException.class);
    }

    // ── detachProject ─────────────────────────────────────────────────────────

    @Test
    void detachProject_ownerCanDetach() {
        JiraProjectEntity p = project("KEY");
        p.setId(PROJECT_ID);
        when(jiraProjectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(p));

        assertThatNoException().isThrownBy(() -> service.detachProject(USER_ID, DS_ID, PROJECT_ID));
        verify(jiraProjectRepository).delete(p);
    }

    @Test
    void detachProject_nonOwner_throwsForbidden() {
        assertThatThrownBy(() -> service.detachProject(99L, DS_ID, PROJECT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void detachProject_wrongDatasource_throwsBadRequest() {
        DataSourceConfig otherDs = new DataSourceConfig();
        otherDs.setId(999L);
        otherDs.setType(DataSourceType.JIRA);
        otherDs.setUser(owner);

        JiraProjectEntity p = project("KEY");
        p.setId(PROJECT_ID);
        p.setDataSource(otherDs);  // belongs to a different DS

        when(jiraProjectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.detachProject(USER_ID, DS_ID, PROJECT_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void detachProject_notFound_throwsNotFoundException() {
        when(jiraProjectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detachProject(USER_ID, DS_ID, PROJECT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JiraProjectEntity project(String key) {
        JiraProjectEntity p = new JiraProjectEntity();
        p.setDataSource(jiraDs);
        p.setProjectKey(key);
        p.setProjectName("Project " + key);
        return p;
    }
}