package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.datasource.service.DataSourceCollectService;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.github.service.GitHubPrCollector;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSourceCollectServiceTest {

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

    private static final Long USER_ID = 1L;
    private static final Long DS_ID = 10L;

    private DataSourceConfig cfg(DataSourceType type) {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(DS_ID);
        cfg.setType(type);
        return cfg;
    }

    private GitRepositoryEntity gitRepo(Long id, String name, boolean collectIssues) {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(id);
        repo.setName(name);
        repo.setRepoFullName(name);
        repo.setCollectIssues(collectIssues);
        return repo;
    }

    @Test
    void collectForDataSource_gitLocal_collectsAndSavesLastSuccessSync() {
        DataSourceConfig cfg = cfg(DataSourceType.GIT_LOCAL);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        GitRepositoryEntity repo = gitRepo(100L, "local-repo", false);
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(gitLocalCollector.collectForRepository(eq(100L), any())).thenReturn(5);
        when(configRepository.save(cfg)).thenReturn(cfg);

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();

        String result = collectService.collectForDataSource(USER_ID, DS_ID, jobState);

        assertThat(jobState.totalPhases).isEqualTo(1);
        assertThat(result).isEqualTo("Local local-repo: 5 commits.");
        assertThat(cfg.getLastSuccessSync()).isNotNull();
        verify(configRepository).save(cfg);
        verify(tracker).setPhase(jobState, "commits", -1);
    }

    @Test
    void collectForDataSource_gitLocal_collectorThrows_logsAndContinuesWithNothingToCollect() {
        DataSourceConfig cfg = cfg(DataSourceType.GIT_LOCAL);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        GitRepositoryEntity repo = gitRepo(101L, "broken-repo", false);
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(gitLocalCollector.collectForRepository(eq(101L), any()))
                .thenThrow(new RuntimeException("repo dir missing"));
        when(configRepository.save(cfg)).thenReturn(cfg);

        String result = collectService.collectForDataSource(USER_ID, DS_ID, null);

        assertThat(result).isEqualTo("Nothing to collect (no repos registered)");
        verify(configRepository).save(cfg);
    }

    @Test
    void collectForDataSource_gitHub_noIssuesRepo_collectsCommitsAndPrsOnly() {
        DataSourceConfig cfg = cfg(DataSourceType.GITHUB);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        GitRepositoryEntity repo = gitRepo(200L, "owner/repo", false);
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(gitHubCollector.collectForRepository(eq(200L), any())).thenReturn(3);
        when(prCollector.collectForRepository(eq(200L), any())).thenReturn(2);
        when(configRepository.save(cfg)).thenReturn(cfg);

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();

        String result = collectService.collectForDataSource(USER_ID, DS_ID, jobState);

        assertThat(jobState.totalPhases).isEqualTo(2);
        assertThat(result).isEqualTo("owner/repo: 3 commits, 2 PRs.");
        verify(tracker).setPhase(jobState, "commits", -1);
        verify(tracker).setPhase(jobState, "pull requests", -1);
        verify(issuesCollector, never())
                .collectIssuesForRepo(any(DataSourceConfig.class), any(GitRepositoryEntity.class));
    }

    @Test
    void collectForDataSource_gitHub_withIssuesRepo_collectsCommitsPrsAndIssues() {
        DataSourceConfig cfg = cfg(DataSourceType.GITHUB);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        GitRepositoryEntity repo = gitRepo(201L, "owner/issues-repo", true);
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(gitHubCollector.collectForRepository(eq(201L), any())).thenReturn(1);
        when(prCollector.collectForRepository(eq(201L), any())).thenReturn(1);
        when(issuesCollector.collectIssuesForRepo(cfg, repo)).thenReturn(4);
        when(configRepository.save(cfg)).thenReturn(cfg);

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();

        String result = collectService.collectForDataSource(USER_ID, DS_ID, jobState);

        assertThat(jobState.totalPhases).isEqualTo(3);
        assertThat(result).isEqualTo("owner/issues-repo: 1 commits, 1 PRs, 4 issues.");
        verify(tracker).setPhase(jobState, "issues", -1);
    }

    @Test
    void collectForDataSource_gitHub_collectorThrows_logsAndContinuesWithNothingToCollect() {
        DataSourceConfig cfg = cfg(DataSourceType.GITHUB);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        GitRepositoryEntity repo = gitRepo(202L, "owner/flaky-repo", false);
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(gitHubCollector.collectForRepository(eq(202L), any()))
                .thenThrow(new RuntimeException("GitHub API rate limited"));
        when(configRepository.save(cfg)).thenReturn(cfg);

        String result = collectService.collectForDataSource(USER_ID, DS_ID, null);

        assertThat(result).isEqualTo("Nothing to collect (no repos registered)");
        verify(prCollector, never()).collectForRepository(any(), any());
    }

    @Test
    void collectForDataSource_jira_noTrackedProjects_logsWarningAndReturnsNothingToCollect() {
        DataSourceConfig cfg = cfg(DataSourceType.JIRA);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);
        when(jiraProjectService.listTrackedProjects(cfg)).thenReturn(List.of());
        when(configRepository.save(cfg)).thenReturn(cfg);

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();

        String result = collectService.collectForDataSource(USER_ID, DS_ID, jobState);

        assertThat(jobState.totalPhases).isEqualTo(1);
        assertThat(result).isEqualTo("Nothing to collect (no repos registered)");
        verify(jiraCollector, never()).collectIssues(any());
    }

    @Test
    void collectForDataSource_jira_withTrackedProjects_collectsIssues() {
        DataSourceConfig cfg = cfg(DataSourceType.JIRA);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        JiraProjectEntity project = new JiraProjectEntity();
        project.setId(300L);
        project.setProjectKey("PDA");
        when(jiraProjectService.listTrackedProjects(cfg)).thenReturn(List.of(project));
        when(jiraCollector.collectIssues(project)).thenReturn(7);
        when(configRepository.save(cfg)).thenReturn(cfg);

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();

        String result = collectService.collectForDataSource(USER_ID, DS_ID, jobState);

        assertThat(result).isEqualTo("Jira[PDA]: 7 issues.");
        verify(tracker).setPhase(jobState, "jira issues", -1);
    }

    @Test
    void collectForDataSource_jira_collectorThrows_appendsFailureToSummary() {
        DataSourceConfig cfg = cfg(DataSourceType.JIRA);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);

        JiraProjectEntity project = new JiraProjectEntity();
        project.setId(301L);
        project.setProjectKey("PDA");
        when(jiraProjectService.listTrackedProjects(cfg)).thenReturn(List.of(project));
        when(jiraCollector.collectIssues(project)).thenThrow(new RuntimeException("Jira API unreachable"));
        when(configRepository.save(cfg)).thenReturn(cfg);

        String result = collectService.collectForDataSource(USER_ID, DS_ID, null);

        assertThat(result).isEqualTo("Jira[PDA] failed: Jira API unreachable");
    }

    @Test
    void collectForDataSource_nullJobStateAndNoRepos_doesNotTouchTracker() {
        DataSourceConfig cfg = cfg(DataSourceType.GIT_LOCAL);
        when(dataSourceService.getForUser(USER_ID, DS_ID)).thenReturn(cfg);
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of());
        when(configRepository.save(cfg)).thenReturn(cfg);

        String result = collectService.collectForDataSource(USER_ID, DS_ID, null);

        assertThat(result).isEqualTo("Nothing to collect (no repos registered)");
        verifyNoInteractions(tracker);
    }
}
