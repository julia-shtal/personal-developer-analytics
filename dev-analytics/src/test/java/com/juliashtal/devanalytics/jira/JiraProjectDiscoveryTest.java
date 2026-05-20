package com.juliashtal.devanalytics.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.dto.DiscoveredProjectDto;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraProjectDiscoveryTest {

    @Mock DataSourceConfigRepository dataSourceConfigRepository;
    @Mock JiraProjectRepository jiraProjectRepository;
    @Mock UserProjectRegistrationRepository userProjectRegistrationRepository;
    @Mock UserRepository userRepository;
    @Mock RestTemplate restTemplate;
    @Mock SimpleTokenEncryptor tokenEncryptor;
    @Spy ObjectMapper objectMapper;

    @InjectMocks JiraProjectService service;

    private static final Long USER_ID = 1L;
    private static final Long DS_ID = 10L;

    private DataSourceConfig jiraDs;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(USER_ID);

        jiraDs = new DataSourceConfig();
        jiraDs.setId(DS_ID);
        jiraDs.setType(DataSourceType.JIRA);
        jiraDs.setUser(owner);
        jiraDs.setBaseUrl("https://mycompany.atlassian.net");
        jiraDs.setApiTokenEncrypted("encrypted");

        when(dataSourceConfigRepository.findById(DS_ID)).thenReturn(Optional.of(jiraDs));
    }

    @Test
    void discoverProjects_returnsWithAlreadyAttachedFlag() throws Exception {
        JiraProjectEntity attached = jiraProject("PROJ-A");
        when(jiraProjectRepository.findAllByDataSource(jiraDs)).thenReturn(List.of(attached));
        when(tokenEncryptor.decrypt("encrypted")).thenReturn("user@example.com:mytoken");

        // Jira API returns two projects; PROJ-A is already tracked, PROJ-B is new
        String apiResponse = """
                {"total":2,"values":[
                  {"key":"PROJ-A","name":"Project A","id":"1"},
                  {"key":"PROJ-B","name":"Project B","id":"2"}
                ]}""";
        ResponseEntity<String> page1 = ResponseEntity.ok(apiResponse);
        when(restTemplate.exchange(any(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(page1);

        List<DiscoveredProjectDto> result = service.discoverProjectsFromJira(USER_ID, DS_ID);

        assertThat(result).hasSize(2);

        DiscoveredProjectDto projA = result.stream()
                .filter(p -> "PROJ-A".equals(p.projectKey())).findFirst().orElseThrow();
        assertThat(projA.alreadyAttached()).isTrue();
        assertThat(projA.projectName()).isEqualTo("Project A");

        DiscoveredProjectDto projB = result.stream()
                .filter(p -> "PROJ-B".equals(p.projectKey())).findFirst().orElseThrow();
        assertThat(projB.alreadyAttached()).isFalse();
        assertThat(projB.projectName()).isEqualTo("Project B");
    }

    @Test
    void discoverProjects_nonOwner_throwsForbidden() {
        assertThatThrownBy(() -> service.discoverProjectsFromJira(99L, DS_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void discoverProjects_nonJiraDs_throwsBadRequest() {
        jiraDs.setType(DataSourceType.GITHUB);

        assertThatThrownBy(() -> service.discoverProjectsFromJira(USER_ID, DS_ID))
                .isInstanceOf(BadRequestException.class);
    }

    private JiraProjectEntity jiraProject(String key) {
        JiraProjectEntity p = new JiraProjectEntity();
        p.setDataSource(jiraDs);
        p.setProjectKey(key);
        p.setProjectName("Project " + key);
        return p;
    }
}