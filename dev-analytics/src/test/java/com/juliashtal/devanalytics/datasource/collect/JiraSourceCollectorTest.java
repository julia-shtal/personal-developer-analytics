package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.service.JiraCollector;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraSourceCollectorTest {

    @Mock JiraProjectService jiraProjectService;
    @Mock JiraCollector jiraCollector;
    @Mock SyncJobTracker tracker;

    @InjectMocks JiraSourceCollector collector;

    @Test
    void supports_returnsJira() {
        assertThat(collector.supports()).isEqualTo(DataSourceType.JIRA);
    }

    @Test
    void collect_noTrackedProjects_returnsZero() {
        DataSourceConfig cfg = new DataSourceConfig();
        when(jiraProjectService.listTrackedProjects(cfg)).thenReturn(List.of());

        int result = collector.collect(cfg, null);

        assertThat(result).isEqualTo(0);
        verify(jiraCollector, never()).collectIssues(any());
    }

    @Test
    void collect_withProjects_delegatesAndReturnsTotal() {
        DataSourceConfig cfg = new DataSourceConfig();
        JiraProjectEntity project = new JiraProjectEntity();
        project.setProjectKey("PDA");
        when(jiraProjectService.listTrackedProjects(cfg)).thenReturn(List.of(project));
        when(jiraCollector.collectIssues(project)).thenReturn(9);

        SyncJobTracker.JobState js = new SyncJobTracker.JobState();
        int result = collector.collect(cfg, js);

        assertThat(result).isEqualTo(9);
        verify(tracker).setPhase(js, "jira issues", -1);
    }

    @Test
    void collect_twoProjects_callsCollectorForEach() {
        DataSourceConfig cfg = new DataSourceConfig();
        JiraProjectEntity p1 = project("PROJ-A");
        JiraProjectEntity p2 = project("PROJ-B");
        when(jiraProjectService.listTrackedProjects(cfg)).thenReturn(List.of(p1, p2));
        when(jiraCollector.collectIssues(p1)).thenReturn(3);
        when(jiraCollector.collectIssues(p2)).thenReturn(7);

        int result = collector.collect(cfg, null);

        assertThat(result).isEqualTo(10);
        verify(jiraCollector).collectIssues(p1);
        verify(jiraCollector).collectIssues(p2);
    }

    @Test
    void collect_oneProjectThrows_continuesAndCountsSuccessful() {
        DataSourceConfig cfg = new DataSourceConfig();
        JiraProjectEntity p1 = project("FAIL");
        JiraProjectEntity p2 = project("OK");
        when(jiraProjectService.listTrackedProjects(cfg)).thenReturn(List.of(p1, p2));
        when(jiraCollector.collectIssues(p1)).thenThrow(new RuntimeException("network error"));
        when(jiraCollector.collectIssues(p2)).thenReturn(5);

        int result = collector.collect(cfg, null);

        assertThat(result).isEqualTo(5);
        verify(jiraCollector).collectIssues(p2);
    }

    private static JiraProjectEntity project(String key) {
        JiraProjectEntity p = new JiraProjectEntity();
        p.setProjectKey(key);
        return p;
    }
}
