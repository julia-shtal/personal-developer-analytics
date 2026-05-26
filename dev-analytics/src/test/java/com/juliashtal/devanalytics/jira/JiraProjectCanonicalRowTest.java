package com.juliashtal.devanalytics.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.JiraUrl;
import com.juliashtal.devanalytics.jira.model.UserProjectRegistration;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepository;
import com.juliashtal.devanalytics.jira.repository.UserProjectRegistrationRepository;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the T4.5 canonical-row model for JiraProjectService.addProject:
 * same-DS idempotency, cross-DS canonical return, and auto-subscribe on attach.
 */
@ExtendWith(MockitoExtension.class)
class JiraProjectCanonicalRowTest {

    @Mock
    JiraProjectRepository jiraProjectRepository;
    @Mock
    UserProjectRegistrationRepository userProjectRegistrationRepository;
    @Mock UserRepository userRepository;
    @Mock DataSourceConfigRepository dataSourceConfigRepository;
    @Mock RestTemplate restTemplate;
    @Mock SimpleTokenEncryptor tokenEncryptor;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    JiraProjectService service;

    private static final String BASE_URL      = "https://acme.atlassian.net";
    private static final String BASE_URL_NORM = "https://acme.atlassian.net"; // already normalized
    private static final String PROJECT_KEY   = "PDA";

    private User userA;
    private User userB;
    private DataSourceConfig dsA;
    private DataSourceConfig dsB;

    @BeforeEach
    void setUp() {
        userA = new User(); userA.setId(1L);
        userB = new User(); userB.setId(2L);

        dsA = new DataSourceConfig();
        dsA.setId(10L);
        dsA.setUser(userA);
        dsA.setBaseUrl(BASE_URL);

        dsB = new DataSourceConfig();
        dsB.setId(20L);
        dsB.setType(DataSourceType.JIRA);
        dsB.setUser(userB);
        dsB.setBaseUrl(BASE_URL + "/");   // trailing slash — must normalize to same key
    }

    // ── addProject: cross-DS canonical return ─────────────────────────────────

    @Test
    void addProject_crossDs_returnsCanonicalRowWithoutInsert() {
        // DS-A already has the canonical row registered.
        JiraProjectEntity canonical = new JiraProjectEntity();
        canonical.setId(100L);
        canonical.setDataSource(dsA);
        canonical.setProjectKey(PROJECT_KEY);
        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(BASE_URL_NORM, PROJECT_KEY))
                .thenReturn(Optional.of(canonical));

        // DS-B calls addProject for the same (baseUrl, projectKey).
        JiraProjectEntity result = service.addProject(dsB, PROJECT_KEY, null);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getDataSource().getId()).isEqualTo(dsA.getId());
        verify(jiraProjectRepository, never()).save(any());
    }

    @Test
    void addProject_trailingSlashInBaseUrl_normalizedToSameKey() {
        // Canonical row was registered without trailing slash.
        JiraProjectEntity canonical = new JiraProjectEntity();
        canonical.setId(100L);
        canonical.setDataSource(dsA);
        canonical.setProjectKey(PROJECT_KEY);
        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(BASE_URL_NORM, PROJECT_KEY))
                .thenReturn(Optional.of(canonical));

        // dsB has a trailing slash in baseUrl — normalization must strip it.
        JiraProjectEntity result = service.addProject(dsB, PROJECT_KEY, null);

        assertThat(result.getId()).isEqualTo(100L);
        verify(jiraProjectRepository, never()).save(any());
    }

    // ── addProject: same-DS idempotency ──────────────────────────────────────

    @Test
    void addProject_sameDsHit_idempotentReturn() {
        JiraProjectEntity existing = new JiraProjectEntity();
        existing.setId(100L);
        existing.setDataSource(dsA);
        existing.setProjectKey(PROJECT_KEY);
        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(BASE_URL_NORM, PROJECT_KEY))
                .thenReturn(Optional.of(existing));

        JiraProjectEntity result = service.addProject(dsA, PROJECT_KEY, null);

        assertThat(result).isSameAs(existing);
        verify(jiraProjectRepository, never()).save(any());
    }

    // ── attachProject: auto-subscribe when canonical row is under another DS ─

    @Test
    void attachProject_crossDs_deletesOrphanDsAndSubscribes() {
        // Setup: dsB owner attaches a project that ds-A already owns canonically.
        dsB.setId(20L);
        when(dataSourceConfigRepository.findById(dsB.getId())).thenReturn(Optional.of(dsB));

        JiraProjectEntity canonical = new JiraProjectEntity();
        canonical.setId(100L);
        canonical.setDataSource(dsA);
        canonical.setProjectKey(PROJECT_KEY);
        canonical.setProjectName("PDA Project");

        when(jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(BASE_URL_NORM, PROJECT_KEY))
                .thenReturn(Optional.of(canonical));
        when(jiraProjectRepository.findById(100L)).thenReturn(Optional.of(canonical));
        when(userRepository.getReferenceById(userB.getId())).thenReturn(userB);
        when(userProjectRegistrationRepository.existsByUserAndProject(userB, canonical)).thenReturn(false);
        // dsB is empty — should be deleted.
        when(jiraProjectRepository.findAllByDataSource(dsB)).thenReturn(List.of());

        var dto = service.attachProject(userB.getId(), dsB.getId(), PROJECT_KEY, null);

        // User B subscribed to canonical project.
        verify(userProjectRegistrationRepository).save(any(UserProjectRegistration.class));
        // dto reflects the canonical project (different dataSourceId).
        assertThat(dto.projectKey()).isEqualTo(PROJECT_KEY);
        assertThat(dto.dataSourceId()).isEqualTo(dsA.getId());
        // No new canonical row.
        verify(jiraProjectRepository, never()).save(any());
        // Orphaned empty DS-B deleted.
        verify(dataSourceConfigRepository).delete(dsB);
    }

    // ── JiraUrl normalization ─────────────────────────────────────────────────

    @Test
    void jiraUrl_normalize_stripsTrailingSlashAndLowercases() {
        assertThat(JiraUrl.normalize("https://ACME.atlassian.net///"))
                .isEqualTo("https://acme.atlassian.net");
        assertThat(JiraUrl.normalize("https://work.atlassian.net"))
                .isEqualTo("https://work.atlassian.net");
        assertThat(JiraUrl.normalize(null)).isNull();
    }
}
