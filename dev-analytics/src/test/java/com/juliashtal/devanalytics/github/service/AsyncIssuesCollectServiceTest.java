package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncIssuesCollectServiceTest {

    @Mock GitHubIssuesCollector issuesCollector;
    @Mock GitRepositoryEntityRepository gitRepoRepository;

    @InjectMocks
    AsyncIssuesCollectService service;

    // ── repo not found → warns and returns without calling collector ─────────

    @Test
    void collectIssuesForRepo_repoNotFound_warnsAndReturns() {
        when(gitRepoRepository.findByIdWithDataSourceConfig(99L)).thenReturn(Optional.empty());

        service.collectIssuesForRepo(99L);

        verifyNoInteractions(issuesCollector);
    }

    // ── repo found → delegates to issuesCollector ───────────────────────────

    @Test
    void collectIssuesForRepo_repoFound_delegatesToCollector() throws Exception {
        DataSourceConfig cfg = new DataSourceConfig();
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(1L);
        repo.setDataSourceConfig(cfg);

        when(gitRepoRepository.findByIdWithDataSourceConfig(1L)).thenReturn(Optional.of(repo));
        when(issuesCollector.collectIssuesForRepo(cfg, repo)).thenReturn(5);

        service.collectIssuesForRepo(1L);

        verify(issuesCollector).collectIssuesForRepo(cfg, repo);
    }

    // ── collector throws → exception swallowed, nothing propagates ──────────

    @Test
    void collectIssuesForRepo_collectorThrows_exceptionSwallowed() throws Exception {
        DataSourceConfig cfg = new DataSourceConfig();
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        repo.setDataSourceConfig(cfg);

        when(gitRepoRepository.findByIdWithDataSourceConfig(2L)).thenReturn(Optional.of(repo));
        when(issuesCollector.collectIssuesForRepo(cfg, repo))
                .thenThrow(new RuntimeException("GitHub down"));

        // Must not throw
        service.collectIssuesForRepo(2L);

        verify(issuesCollector).collectIssuesForRepo(cfg, repo);
    }
}
