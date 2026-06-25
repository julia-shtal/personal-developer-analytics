package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.SyncJobTracker;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitLocalSourceCollectorTest {

    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock GitLocalCollector gitLocalCollector;
    @Mock SyncJobTracker tracker;

    @InjectMocks GitLocalSourceCollector collector;

    @Test
    void supports_returnsGitLocal() {
        assertThat(collector.supports()).isEqualTo(DataSourceType.GIT_LOCAL);
    }

    @Test
    void phaseCount_alwaysOne() {
        assertThat(collector.phaseCount(new DataSourceConfig())).isEqualTo(1);
    }

    @Test
    void collect_singleRepo_delegatesAndReturnsTotalCount() {
        DataSourceConfig cfg = new DataSourceConfig();
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(1L);
        repo.setName("my-repo");
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(gitLocalCollector.collectForRepository(eq(1L), any())).thenReturn(7);

        SyncJobTracker.JobState jobState = new SyncJobTracker.JobState();
        int result = collector.collect(cfg, jobState);

        assertThat(result).isEqualTo(7);
        verify(tracker).setPhase(jobState, "commits", -1);
    }

    @Test
    void collect_collectorThrows_swallowsExceptionAndReturnsZero() {
        DataSourceConfig cfg = new DataSourceConfig();
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        repo.setName("broken");
        when(gitRepoRepository.findAllByDataSourceConfig(cfg)).thenReturn(List.of(repo));
        when(gitLocalCollector.collectForRepository(eq(2L), any()))
                .thenThrow(new RuntimeException("disk error"));

        int result = collector.collect(cfg, null);

        assertThat(result).isEqualTo(0);
    }
}
