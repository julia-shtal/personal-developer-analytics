package com.juliashtal.devanalytics.issue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.model.IssueSource;
import com.juliashtal.devanalytics.jira.JiraCollector;
import com.juliashtal.devanalytics.jira.JiraProjectRepository;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueSourceDiscriminatorTest {

    @Mock RestTemplate restTemplate;
    @Mock IssueRepository issueRepository;
    @Mock JiraProjectRepository jiraProjectRepository;
    @Mock SimpleTokenEncryptor tokenEncryptor;
    @Spy  ObjectMapper objectMapper;

    @InjectMocks JiraCollector jiraCollector;

    private static final Long PROJECT_ID = 1L;
    private JiraProjectEntity project;

    @BeforeEach
    void setUp() {
        DataSourceConfig ds = new DataSourceConfig();
        ds.setId(10L);
        ds.setType(DataSourceType.JIRA);
        ds.setBaseUrl("https://mycompany.atlassian.net");
        ds.setApiTokenEncrypted("enc");

        project = new JiraProjectEntity();
        project.setId(PROJECT_ID);
        project.setDataSource(ds);
        project.setProjectKey("PDA");
        project.setProjectName("Personal Dev Analytics");

        when(jiraProjectRepository.getReferenceById(PROJECT_ID)).thenReturn(project);
        when(tokenEncryptor.decrypt("enc")).thenReturn("user@example.com:mytoken");

        // /rest/api/3/myself for account ID lookup — lenient: not used by entity-level tests
        lenient().when(restTemplate.exchange(
                argThat(uri -> uri != null && uri.toString().contains("/rest/api/3/myself")),
                eq(HttpMethod.GET), any(), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"accountId\":\"abc123\"}"));

        // /rest/api/3/search/jql — one issue, then empty page to stop
        String oneIssue = """
            {"total":1,"issues":[
              {"key":"PDA-1","fields":{
                "summary":"Fix the bug",
                "description":null,
                "assignee":null,
                "reporter":null,
                "created":"2024-01-15T10:00:00.000+0000",
                "updated":"2024-01-15T10:00:00.000+0000",
                "resolutiondate":null,
                "status":{"name":"In Progress"},
                "labels":[]
              }}
            ]}""";
        lenient().when(restTemplate.exchange(
                argThat(uri -> uri != null && uri.toString().contains("/rest/api/3/search/jql")),
                eq(HttpMethod.GET), any(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(oneIssue));

        lenient().when(issueRepository.findByJiraProjectAndSourceIssueKey(any(), any()))
            .thenReturn(Optional.empty());
        lenient().when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void collectIssues_setsSourceJira() {
        ArgumentCaptor<IssueEntity> captor = ArgumentCaptor.forClass(IssueEntity.class);

        jiraCollector.collectIssues(project);

        verify(issueRepository).save(captor.capture());
        IssueEntity saved = captor.getValue();

        assertThat(saved.getSource()).isEqualTo(IssueSource.JIRA);
    }

    @Test
    void collectIssues_setsSourceContextToProjectKey() {
        ArgumentCaptor<IssueEntity> captor = ArgumentCaptor.forClass(IssueEntity.class);

        jiraCollector.collectIssues(project);

        verify(issueRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceContext()).isEqualTo("PDA");
    }

    @Test
    void collectIssues_setsJiraProjectOnSavedEntity() {
        ArgumentCaptor<IssueEntity> captor = ArgumentCaptor.forClass(IssueEntity.class);

        jiraCollector.collectIssues(project);

        verify(issueRepository).save(captor.capture());
        assertThat(captor.getValue().getJiraProject()).isEqualTo(project);
    }

}
