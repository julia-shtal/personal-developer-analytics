package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.datasource.service.DataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.github.service.GitHubPrCollector;
import com.juliashtal.devanalytics.jira.service.JiraCollector;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraCollectorMultiProjectTest {

    @Mock DataSourceConfigRepository configRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock GitLocalCollector gitLocalCollector;
    @Mock GitHubCollector gitHubCollector;
    @Mock GitHubPrCollector prCollector;
    @Mock GitHubIssuesCollector issuesCollector;
    @Mock JiraCollector jiraCollector;
    @Mock JiraProjectService jiraProjectService;
    @Mock DataSourceService dataSourceService;
    @Mock SyncJobTracker tracker;

    @InjectMocks DataSourceCollectService collectService;

    private DataSourceConfig jiraDs;

    @BeforeEach
    void setUp() {
        jiraDs = new DataSourceConfig();
        jiraDs.setId(20L);
        jiraDs.setType(DataSourceType.JIRA);
        when(dataSourceService.getForUser(1L, 20L)).thenReturn(jiraDs);
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void collectJira_twoProjects_callsCollectorForEach() {
        JiraProjectEntity p1 = project("PROJ-A");
        JiraProjectEntity p2 = project("PROJ-B");
        when(jiraProjectService.listTrackedProjects(jiraDs)).thenReturn(List.of(p1, p2));
        when(jiraCollector.collectIssues(p1)).thenReturn(3);
        when(jiraCollector.collectIssues(p2)).thenReturn(7);

        String summary = collectService.collectForDataSource(1L, 20L, null);

        verify(jiraCollector).collectIssues(p1);
        verify(jiraCollector).collectIssues(p2);
        assertThat(summary).contains("PROJ-A").contains("PROJ-B");
    }

    @Test
    void collectJira_zeroProjects_returnsNothingToCollect() {
        when(jiraProjectService.listTrackedProjects(jiraDs)).thenReturn(List.of());

        String summary = collectService.collectForDataSource(1L, 20L, null);

        verify(jiraCollector, never()).collectIssues(any());
        assertThat(summary).contains("Nothing to collect");
    }

    @Test
    void collectJira_collectorThrows_continuesOtherProjects() {
        JiraProjectEntity p1 = project("FAIL");
        JiraProjectEntity p2 = project("OK");
        when(jiraProjectService.listTrackedProjects(jiraDs)).thenReturn(List.of(p1, p2));
        when(jiraCollector.collectIssues(p1)).thenThrow(new RuntimeException("network error"));
        when(jiraCollector.collectIssues(p2)).thenReturn(5);

        String summary = collectService.collectForDataSource(1L, 20L, null);

        verify(jiraCollector).collectIssues(p1);
        verify(jiraCollector).collectIssues(p2);
        assertThat(summary).contains("OK").contains("5 issues");
    }

    private JiraProjectEntity project(String key) {
        JiraProjectEntity p = new JiraProjectEntity();
        p.setProjectKey(key);
        p.setDataSource(jiraDs);
        return p;
    }
}
