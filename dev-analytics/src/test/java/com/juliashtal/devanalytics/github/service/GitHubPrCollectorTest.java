package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
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
class GitHubPrCollectorTest {

    @Mock GitHubPullRequestCollector ingestService;
    @Mock GitHubPrStatsEnrichmentService enrichmentService;

    @InjectMocks
    GitHubPrCollector collector;

    private GitHubPullRequestEntity pr(Instant createdAt, StatsStatus status, String repoFullName) {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setRepoFullName(repoFullName);
        repo.setName(repoFullName);

        GitHubPullRequestEntity p = new GitHubPullRequestEntity();
        p.setCreatedAt(createdAt);
        p.setStatsStatus(status);
        p.setRepository(repo);
        return p;
    }

    // ── empty ingest → returns 0, enrichment never called ──────────────────

    @Test
    void collectForRepository_emptyIngestResult_returnsZero() {
        when(ingestService.collectPullRequests(eq(1L), any()))
                .thenReturn(new GitHubPullRequestCollector.IngestResult(
                        new ArrayList<>(), "https://api.github.com", "token"));

        int result = collector.collectForRepository(1L, null);

        assertThat(result).isEqualTo(0);
        verifyNoInteractions(enrichmentService);
    }

    // ── with new PRs → enriches and returns count ───────────────────────────

    @Test
    void collectForRepository_withNewPrs_enrichesAndReturnsCount() {
        GitHubPullRequestEntity p1 = pr(Instant.parse("2024-01-01T00:00:00Z"),
                StatsStatus.PENDING, "owner/repo");
        GitHubPullRequestEntity p2 = pr(Instant.parse("2024-01-03T00:00:00Z"),
                StatsStatus.PENDING, "owner/repo");

        when(ingestService.collectPullRequests(eq(1L), any()))
                .thenReturn(new GitHubPullRequestCollector.IngestResult(
                        new ArrayList<>(List.of(p1, p2)), "https://api.github.com", "token"));

        int result = collector.collectForRepository(1L, null);

        assertThat(result).isEqualTo(2);
        verify(enrichmentService).enrichImmediate(
                argThat(list -> list.size() == 2),
                eq("https://api.github.com"),
                eq("token"),
                eq("owner/repo"));
    }

    // ── PENDING remaining → still counted ──────────────────────────────────

    @Test
    void collectForRepository_pendingRemainsAfterEnrichment_returnsTotalCount() {
        GitHubPullRequestEntity p = pr(Instant.parse("2024-01-01T00:00:00Z"),
                StatsStatus.PENDING, "owner/repo");

        when(ingestService.collectPullRequests(eq(2L), any()))
                .thenReturn(new GitHubPullRequestCollector.IngestResult(
                        new ArrayList<>(List.of(p)), "https://api.github.com", "token"));

        int result = collector.collectForRepository(2L, null);

        assertThat(result).isEqualTo(1);
    }

    // ── no repoFullName → falls back to getName() ──────────────────────────

    @Test
    void collectForRepository_noRepoFullName_usesNameForEnrichment() {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setRepoFullName(null);
        repo.setName("local-repo");

        GitHubPullRequestEntity p = new GitHubPullRequestEntity();
        p.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        p.setStatsStatus(StatsStatus.COMPLETE);
        p.setRepository(repo);

        when(ingestService.collectPullRequests(eq(3L), any()))
                .thenReturn(new GitHubPullRequestCollector.IngestResult(
                        new ArrayList<>(List.of(p)), "https://api.github.com", "token"));

        collector.collectForRepository(3L, null);

        verify(enrichmentService).enrichImmediate(any(), any(), any(), eq("local-repo"));
    }
}
