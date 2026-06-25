package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.service.GitHubCollector;
import com.juliashtal.devanalytics.github.service.GitHubIssuesCollector;
import com.juliashtal.devanalytics.github.service.GitHubPrCollector;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubSourceCollectorTest {

    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock GitHubCollector gitHubCollector;
    @Mock GitHubPrCollector prCollector;
    @Mock GitHubIssuesCollector issuesCollector;
    @Mock SyncJobTracker tracker;

    @InjectMocks GitHubSourceCollector collector;

    private GitRepositoryEntity repo(Long id, String name, boolean collectIssues) {
        GitRepositoryEntity r = new GitRepositoryEntity();
        r.setId(id);
        r.setName(name);
        r.setRepoFullName(name);
        r.setCollectIssues(collectIssues);
        return r;
    }

    @Test
    void supports_returnsGitHub() {
        assertThat(collector.supports()).isEqualTo(DataSourceType.GITHUB);
    }

    @Test
    void phaseCount_noIssues_returnsTwo() {
        DataSourceConfig cfg = new DataSourceConfig();
        when(gitRepoRepository.findAllByDataSourceConfig(cfg))
                .thenReturn(List.of(repo(1L, "r", false)));
        assertThat(collector.phaseCount(cfg)).isEqualTo(2);
    }

    @Test
    void phaseCount_withIssues_returnsThree() {
        DataSourceConfig cfg = new DataSourceConfig();
        when(gitRepoRepository.findAllByDataSourceConfig(cfg))
                .thenReturn(List.of(repo(1L, "r", true)));
        assertThat(collector.phaseCount(cfg)).isEqualTo(3);
    }

    @Test
    void collect_noIssues_collectsCommitsAndPrsOnly() {
        DataSourceConfig cfg = new DataSourceConfig();
        GitRepositoryEntity r = repo(10L, "owner/repo", false);
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(r));
        when(gitHubCollector.collectForRepository(eq(10L), any())).thenReturn(3);
        when(prCollector.collectForRepository(eq(10L), any())).thenReturn(2);

        SyncJobTracker.JobState js = new SyncJobTracker.JobState();
        int result = collector.collect(cfg, js);

        assertThat(result).isEqualTo(5);
        verify(issuesCollector, never()).collectIssuesForRepo(
                any(DataSourceConfig.class), any(GitRepositoryEntity.class));
    }

    @Test
    void collect_withIssues_collectsAllThree() {
        DataSourceConfig cfg = new DataSourceConfig();
        GitRepositoryEntity r = repo(11L, "owner/repo-issues", true);
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(r));
        when(gitHubCollector.collectForRepository(eq(11L), any())).thenReturn(2);
        when(prCollector.collectForRepository(eq(11L), any())).thenReturn(1);
        when(issuesCollector.collectIssuesForRepo(
                any(DataSourceConfig.class), any(GitRepositoryEntity.class))).thenReturn(4);

        int result = collector.collect(cfg, null);

        assertThat(result).isEqualTo(7);
    }
}
