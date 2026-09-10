package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for the per-repository identity backfill.
 *
 * <p>The only path that reaches records collected before attribution existed, since every ingest
 * path is incremental and never revisits them. Each step is idempotent — rows already carrying
 * the right id are skipped without a write — so a retry costs API calls and nothing else.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WireMockTest
class GitHubIdentityBackfillServiceTest {

    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubPullRequestRepository prRepository;
    @Mock GitHubPrStatsEnrichmentService prEnrichmentService;
    @Mock GitHubIssuesCollector issuesCollector;
    @Mock GitHubClientFactory clientFactory;

    GitHubIdentityBackfillService service;
    GitRepositoryEntity repo;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        service = new GitHubIdentityBackfillService(commitRepository, prRepository,
                prEnrichmentService, issuesCollector, clientFactory, new ObjectMapper());

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(1L);
        cfg.setBaseUrl(wm.getHttpBaseUrl());

        repo = new GitRepositoryEntity();
        repo.setId(10L);
        repo.setName("owner/legacy-repo");
        repo.setDataSourceConfig(cfg);

        when(clientFactory.getDecryptedToken(any())).thenReturn("ghp_token");
    }

    // ── commits ─────────────────────────────────────────────────────────────

    @Test
    void backfillCommits_commitWithResolvedAuthor_setsIdAndLoginOnTheStoredRow() {
        GitCommitEntity stored = commit("abc123", null);
        when(commitRepository.findByHash("abc123")).thenReturn(Optional.of(stored));
        stubCommitsPage("""
            [{"sha": "abc123", "author": {"id": 49405289, "login": "julia-shtal"}}]
            """);

        service.backfillCommits(repo);

        assertThat(stored.getAuthorGithubId()).isEqualTo(49405289L);
        assertThat(stored.getAuthorGithubLogin()).isEqualTo("julia-shtal");
        verify(commitRepository).saveAll(any());
    }

    @Test
    void backfillCommits_commitAlreadyCarryingTheId_writesNothing() {
        GitCommitEntity stored = commit("abc123", 49405289L);
        when(commitRepository.findByHash("abc123")).thenReturn(Optional.of(stored));
        stubCommitsPage("""
            [{"sha": "abc123", "author": {"id": 49405289, "login": "julia-shtal"}}]
            """);

        service.backfillCommits(repo);

        // Idempotence: a second run over a finished repo must not churn rows.
        verify(commitRepository, never()).saveAll(any());
    }

    @Test
    void backfillCommits_commitWithNullAuthor_isSkipped() {
        stubCommitsPage("""
            [{"sha": "def456", "author": null}]
            """);

        service.backfillCommits(repo);

        // GitHub resolved no account, so there is nothing to write and no row to look up.
        verify(commitRepository, never()).findByHash(any());
        verify(commitRepository, never()).saveAll(any());
    }

    @Test
    void backfillCommits_commitNotStoredLocally_isSkipped() {
        when(commitRepository.findByHash("unknown")).thenReturn(Optional.empty());
        stubCommitsPage("""
            [{"sha": "unknown", "author": {"id": 1, "login": "someone"}}]
            """);

        service.backfillCommits(repo);

        verify(commitRepository, never()).saveAll(any());
    }

    @Test
    void backfillCommits_gitHubReturnsError_throwsGitHubException() {
        stubFor(get(urlPathMatching("/repos/owner/legacy-repo/commits.*"))
                .willReturn(aResponse().withStatus(502)));

        // Must throw so the caller leaves identity_backfilled_at null and retries the repo.
        assertThatThrownBy(() -> service.backfillCommits(repo))
                .isInstanceOf(GitHubException.class);
    }

    @Test
    void backfillCommits_nonArrayBodyWithStatus200_throwsRatherThanEndingSilently() {
        stubFor(get(urlPathMatching("/repos/owner/legacy-repo/commits.*"))
                .willReturn(okJson("{\"message\": \"Bad credentials\"}")));

        // Treating this as "no more pages" would stamp the repo done having migrated nothing.
        assertThatThrownBy(() -> service.backfillCommits(repo))
                .isInstanceOf(GitHubException.class);
    }

    // ── pull requests ───────────────────────────────────────────────────────

    @Test
    void backfillPullRequests_prWithUser_setsAuthorGithubIdByNumber() {
        GitHubPullRequestEntity stored = new GitHubPullRequestEntity();
        stored.setNumber(42);
        when(prRepository.findByRepositoryAndNumber(repo, 42)).thenReturn(Optional.of(stored));
        stubPullsPage("""
            [{"number": 42, "user": {"id": 49405289, "login": "julia-shtal"}}]
            """);

        service.backfillPullRequests(repo);

        assertThat(stored.getAuthorGithubId()).isEqualTo(49405289L);
        verify(prRepository).saveAll(any());
    }

    @Test
    void backfillPullRequests_prAlreadyCarryingTheId_writesNothing() {
        GitHubPullRequestEntity stored = new GitHubPullRequestEntity();
        stored.setNumber(42);
        stored.setAuthorGithubId(49405289L);
        when(prRepository.findByRepositoryAndNumber(repo, 42)).thenReturn(Optional.of(stored));
        stubPullsPage("""
            [{"number": 42, "user": {"id": 49405289, "login": "julia-shtal"}}]
            """);

        service.backfillPullRequests(repo);

        verify(prRepository, never()).saveAll(any());
    }

    @Test
    void backfillPullRequests_prWithNullUser_isSkipped() {
        stubPullsPage("""
            [{"number": 43, "user": null}]
            """);

        service.backfillPullRequests(repo);

        verify(prRepository, never()).findByRepositoryAndNumber(any(), anyInt());
    }

    // ── reviews ─────────────────────────────────────────────────────────────

    @Test
    void refreshReviews_everyStoredPr_isRefreshedThroughEnrichment() throws Exception {
        GitHubPullRequestEntity pr = new GitHubPullRequestEntity();
        pr.setNumber(42);
        when(prRepository.findByRepository(repo)).thenReturn(List.of(pr));

        service.refreshReviews(repo);

        // Enrichment would never revisit this PR on its own once its stats are COMPLETE.
        verify(prEnrichmentService).refreshReviews(eq(pr), any(), eq("ghp_token"), eq("owner/legacy-repo"));
    }

    @Test
    void refreshReviews_oneUnreachablePr_throwsSoTheRepositoryStaysPending() throws Exception {
        GitHubPullRequestEntity pr = new GitHubPullRequestEntity();
        pr.setNumber(42);
        when(prRepository.findByRepository(repo)).thenReturn(List.of(pr));
        doThrow(new java.io.IOException("boom"))
                .when(prEnrichmentService).refreshReviews(any(), any(), any(), any());

        assertThatThrownBy(() -> service.refreshReviews(repo))
                .isInstanceOf(GitHubException.class);
    }

    // ── issues ──────────────────────────────────────────────────────────────

    @Test
    void backfillIssues_repositoryWithIssueCollectionOn_runsTheCollector() {
        repo.setCollectIssues(true);

        service.backfillIssues(repo);

        verify(issuesCollector).collectIssuesForRepo(repo.getDataSourceConfig(), repo);
    }

    @Test
    void backfillIssues_repositoryWithIssueCollectionOff_doesNothing() {
        repo.setCollectIssues(false);

        service.backfillIssues(repo);

        verifyNoInteractions(issuesCollector);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private GitCommitEntity commit(String hash, Long authorGithubId) {
        GitCommitEntity c = new GitCommitEntity();
        c.setHash(hash);
        c.setAuthorGithubId(authorGithubId);
        return c;
    }

    private void stubCommitsPage(String body) {
        stubFor(get(urlPathMatching("/repos/owner/legacy-repo/commits.*")).willReturn(okJson(body)));
    }

    private void stubPullsPage(String body) {
        stubFor(get(urlPathMatching("/repos/owner/legacy-repo/pulls.*")).willReturn(okJson(body)));
    }

    @SuppressWarnings("unused")
    private ArgumentCaptor<List<GitCommitEntity>> commitCaptor() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GitCommitEntity>> captor = ArgumentCaptor.forClass(List.class);
        return captor;
    }
}
