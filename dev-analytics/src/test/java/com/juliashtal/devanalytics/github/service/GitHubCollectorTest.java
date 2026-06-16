package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubCollectorTest {

    @Mock GitHubCommitIngestService ingestService;
    @Mock GitHubCommitStatsEnrichmentService enrichmentService;

    @InjectMocks
    GitHubCollector collector;

    private GitCommitEntity commit(Instant date, StatsStatus status, String repoFullName) {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setRepoFullName(repoFullName);

        GitCommitEntity c = new GitCommitEntity();
        c.setAuthorDate(date);
        c.setStatsStatus(status);
        c.setRepository(repo);
        return c;
    }

    // ── empty ingest → returns 0, enrichment never called ──────────────────

    @Test
    void collectForRepository_emptyIngestResult_returnsZero() {
        when(ingestService.ingestForRepository(eq(1L), any()))
                .thenReturn(new GitHubCommitIngestService.IngestResult(
                        new ArrayList<>(), "https://api.github.com", "token"));

        int result = collector.collectForRepository(1L, null);

        assertThat(result).isEqualTo(0);
        verifyNoInteractions(enrichmentService);
    }

    // ── with commits → enriches newest-first and returns count ─────────────

    @Test
    void collectForRepository_withNewCommits_enrichesAndReturnsCount() {
        GitCommitEntity c1 = commit(Instant.parse("2024-01-01T00:00:00Z"), StatsStatus.COMPLETE, "owner/repo");
        GitCommitEntity c2 = commit(Instant.parse("2024-01-02T00:00:00Z"), StatsStatus.COMPLETE, "owner/repo");

        when(ingestService.ingestForRepository(eq(1L), any()))
                .thenReturn(new GitHubCommitIngestService.IngestResult(
                        new ArrayList<>(List.of(c1, c2)), "https://api.github.com", "token"));

        int result = collector.collectForRepository(1L, null);

        assertThat(result).isEqualTo(2);
        verify(enrichmentService).enrichImmediate(
                argThat(list -> list.size() == 2),
                eq("https://api.github.com"),
                eq("token"),
                eq("owner/repo"));
    }

    // ── PENDING remaining after enrichment → still counted ─────────────────

    @Test
    void collectForRepository_pendingRemainsAfterEnrichment_returnsTotalCount() {
        GitCommitEntity c = commit(Instant.parse("2024-01-01T00:00:00Z"), StatsStatus.PENDING, "owner/repo");

        when(ingestService.ingestForRepository(eq(2L), any()))
                .thenReturn(new GitHubCommitIngestService.IngestResult(
                        new ArrayList<>(List.of(c)), "https://api.github.com", "token"));

        int result = collector.collectForRepository(2L, null);

        assertThat(result).isEqualTo(1);
    }

    // ── no repoFullName → falls back to getName() ──────────────────────────

    @Test
    void collectForRepository_noRepoFullName_usesNameForEnrichment() {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setRepoFullName(null);
        repo.setName("local-repo");

        GitCommitEntity c = new GitCommitEntity();
        c.setAuthorDate(Instant.parse("2024-01-01T00:00:00Z"));
        c.setStatsStatus(StatsStatus.COMPLETE);
        c.setRepository(repo);

        when(ingestService.ingestForRepository(eq(3L), any()))
                .thenReturn(new GitHubCommitIngestService.IngestResult(
                        new ArrayList<>(List.of(c)), "https://api.github.com", "token"));

        collector.collectForRepository(3L, null);

        verify(enrichmentService).enrichImmediate(any(), any(), any(), eq("local-repo"));
    }
}
