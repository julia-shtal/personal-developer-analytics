package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommitStatsEnrichmentSchedulerTest {

    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubPullRequestRepository prRepository;
    @Mock GitRepositoryEntityRepository repoRepository;
    @Mock GitHubCommitStatsEnrichmentService commitEnrichmentService;
    @Mock GitHubPrStatsEnrichmentService prEnrichmentService;
    @Mock GitHubClientFactory clientFactory;

    @InjectMocks
    CommitStatsEnrichmentScheduler scheduler;

    // ── no repos with PENDING → exits immediately ───────────────────────────

    @Test
    void enrichPending_noReposWithPending_doesNothing() {
        when(commitRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of());
        when(prRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of());

        scheduler.enrichPending();

        verifyNoInteractions(repoRepository);
        verifyNoInteractions(commitEnrichmentService);
        verifyNoInteractions(prEnrichmentService);
    }

    // ── repo entity not found → silently skips ─────────────────────────────

    @Test
    void enrichPending_repoEntityNotFound_skipsWithoutException() {
        when(commitRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of(99L));
        when(prRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of());
        when(repoRepository.findByIdWithDataSourceConfig(99L)).thenReturn(Optional.empty());

        scheduler.enrichPending();

        verifyNoInteractions(commitEnrichmentService);
        verifyNoInteractions(prEnrichmentService);
    }

    // ── GITHUB-type repo uses repoFullName ─────────────────────────────────

    @Test
    void enrichPending_githubTypeRepo_usesRepoFullName() {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setBaseUrl("https://api.github.com");

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(1L);
        repo.setRepoType(RepoType.GITHUB);
        repo.setRepoFullName("owner/my-repo");
        repo.setName("owner/my-repo");
        repo.setDataSourceConfig(cfg);

        when(commitRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of(1L));
        when(prRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of());
        when(repoRepository.findByIdWithDataSourceConfig(1L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(cfg)).thenReturn("ghp_token");
        when(commitEnrichmentService.processPendingBatchForRepo(any(), any(), any(), any()))
                .thenReturn(0);
        when(prEnrichmentService.processPendingBatchForRepo(any(), any(), any(), any()))
                .thenReturn(0);

        scheduler.enrichPending();

        verify(commitEnrichmentService).processPendingBatchForRepo(
                any(), eq("ghp_token"), eq("owner/my-repo"), eq(1L));
    }

    // ── non-GITHUB-type repo uses getName() ────────────────────────────────

    @Test
    void enrichPending_localTypeRepo_usesName() {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setBaseUrl("https://api.github.com");

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(2L);
        repo.setRepoType(RepoType.LOCAL);
        repo.setRepoFullName(null);
        repo.setName("local-repo-name");
        repo.setDataSourceConfig(cfg);

        when(commitRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of(2L));
        when(prRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of());
        when(repoRepository.findByIdWithDataSourceConfig(2L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(cfg)).thenReturn("token");
        when(commitEnrichmentService.processPendingBatchForRepo(any(), any(), any(), any()))
                .thenReturn(0);
        when(prEnrichmentService.processPendingBatchForRepo(any(), any(), any(), any()))
                .thenReturn(0);

        scheduler.enrichPending();

        verify(commitEnrichmentService).processPendingBatchForRepo(
                any(), any(), eq("local-repo-name"), eq(2L));
    }

    // ── exception during enrichment → logs warning, continues ──────────────

    @Test
    void enrichPending_enrichmentThrows_logsWarningAndContinues() {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setBaseUrl("https://api.github.com");

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(3L);
        repo.setRepoType(RepoType.GITHUB);
        repo.setRepoFullName("owner/failing-repo");
        repo.setName("owner/failing-repo");
        repo.setDataSourceConfig(cfg);

        when(commitRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of(3L));
        when(prRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of());
        when(repoRepository.findByIdWithDataSourceConfig(3L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(cfg)).thenReturn("token");
        when(commitEnrichmentService.processPendingBatchForRepo(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        // Must not propagate — scheduler swallows per-repo exceptions.
        scheduler.enrichPending();
    }

    // ── same repo in both lists → processed once, both services called ──────

    @Test
    void enrichPending_repoInBothLists_processedOnceAndBothServicesCalled() {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setBaseUrl("https://api.github.com");

        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(4L);
        repo.setRepoType(RepoType.GITHUB);
        repo.setRepoFullName("owner/full-repo");
        repo.setName("owner/full-repo");
        repo.setDataSourceConfig(cfg);

        // Same repo ID appears in both commit and PR pending lists.
        when(commitRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of(4L));
        when(prRepository.findRepositoryIdsWithStatsStatus(StatsStatus.PENDING))
                .thenReturn(List.of(4L));
        when(repoRepository.findByIdWithDataSourceConfig(4L)).thenReturn(Optional.of(repo));
        when(clientFactory.getDecryptedToken(cfg)).thenReturn("token");
        when(commitEnrichmentService.processPendingBatchForRepo(any(), any(), any(), any()))
                .thenReturn(3);
        when(prEnrichmentService.processPendingBatchForRepo(any(), any(), any(), any()))
                .thenReturn(2);

        scheduler.enrichPending();

        // LinkedHashSet dedup: repo looked up exactly once.
        verify(repoRepository, times(1)).findByIdWithDataSourceConfig(4L);
        // Both services called exactly once.
        verify(commitEnrichmentService, times(1))
                .processPendingBatchForRepo(any(), any(), eq("owner/full-repo"), eq(4L));
        verify(prEnrichmentService, times(1))
                .processPendingBatchForRepo(any(), any(), eq("owner/full-repo"), eq(4L));
    }
}
