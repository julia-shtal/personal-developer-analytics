package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@WireMockTest
class GitHubCommitStatsEnrichmentServiceTest {

    @Mock GitCommitEntityRepository commitRepository;

    GitHubCommitStatsEnrichmentService service;
    String wmBaseUrl;

    private static final String REPO_NAME = "owner/test-repo";
    private static final String SHA = "abc123";

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        service = new GitHubCommitStatsEnrichmentService(commitRepository, new ObjectMapper());
        wmBaseUrl = wm.getHttpBaseUrl();
    }

    private GitCommitEntity commit(int attempts) {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(10L);

        GitCommitEntity c = new GitCommitEntity();
        c.setId(1L);
        c.setHash(SHA);
        c.setRepository(repo);
        c.setStatsAttempts(attempts);
        c.setStatsStatus(StatsStatus.PENDING);
        return c;
    }

    // ── max attempts exceeded → FAILED ─────────────────────────────────────

    @Test
    void enrichSingle_maxAttemptsExceeded_marksFailed() {
        // MAX_ATTEMPTS = 3; attempts starts at 3, incremented to 4 → 4 > 3 → FAILED
        GitCommitEntity commit = commit(3);

        service.enrichSingle(commit, wmBaseUrl, "token", REPO_NAME);

        assertThat(commit.getStatsAttempts()).isEqualTo(4);
        assertThat(commit.getStatsStatus()).isEqualTo(StatsStatus.FAILED);
        assertThat(commit.getStatsFetchedAt()).isNotNull();
    }

    // ── 200 response → COMPLETE with stats ─────────────────────────────────

    @Test
    void enrichSingle_successResponse_marksCompleteWithStats() {
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"stats":{"additions":10,"deletions":3},
                                 "files":[{"filename":"a.txt"},{"filename":"b.txt"}]}""")));

        GitCommitEntity commit = commit(0);
        service.enrichSingle(commit, wmBaseUrl, "token", REPO_NAME);

        assertThat(commit.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
        assertThat(commit.getAdditions()).isEqualTo(10);
        assertThat(commit.getDeletions()).isEqualTo(3);
        assertThat(commit.getFilesChanged()).isEqualTo(2);
        assertThat(commit.getStatsFetchedAt()).isNotNull();
    }

    // ── 403 "too large" → SKIPPED ──────────────────────────────────────────

    @Test
    void enrichSingle_403TooLarge_marksSkipped() {
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .willReturn(aResponse().withStatus(403)
                        .withBody("diff is too large to process")));

        GitCommitEntity commit = commit(0);
        service.enrichSingle(commit, wmBaseUrl, "token", REPO_NAME);

        assertThat(commit.getStatsStatus()).isEqualTo(StatsStatus.SKIPPED);
        assertThat(commit.getStatsFetchedAt()).isNotNull();
    }

    // ── 403 secondary rate limit → leave PENDING ───────────────────────────

    @Test
    void enrichSingle_403SecondaryRateLimit_leavesPending() {
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .willReturn(aResponse().withStatus(403)
                        .withBody("secondary rate limit triggered")));

        GitCommitEntity commit = commit(0);
        service.enrichSingle(commit, wmBaseUrl, "token", REPO_NAME);

        assertThat(commit.getStatsStatus()).isEqualTo(StatsStatus.PENDING);
        assertThat(commit.getStatsFetchedAt()).isNull();
    }

    // ── 404 → SKIPPED ──────────────────────────────────────────────────────

    @Test
    void enrichSingle_404_marksSkipped() {
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        GitCommitEntity commit = commit(0);
        service.enrichSingle(commit, wmBaseUrl, "token", REPO_NAME);

        assertThat(commit.getStatsStatus()).isEqualTo(StatsStatus.SKIPPED);
    }

    // ── 422 → SKIPPED ──────────────────────────────────────────────────────

    @Test
    void enrichSingle_422_marksSkipped() {
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .willReturn(aResponse().withStatus(422).withBody("unprocessable entity")));

        GitCommitEntity commit = commit(0);
        service.enrichSingle(commit, wmBaseUrl, "token", REPO_NAME);

        assertThat(commit.getStatsStatus()).isEqualTo(StatsStatus.SKIPPED);
    }

    // ── unexpected status → stays PENDING ──────────────────────────────────

    @Test
    void enrichSingle_unexpectedStatus_leavesPending() {
        // Retry-After: 0 avoids the 5-second INITIAL_BACKOFF_MS sleep on 5xx.
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Retry-After", "0")
                        .withBody("server error")));

        GitCommitEntity commit = commit(0);
        service.enrichSingle(commit, wmBaseUrl, "token", REPO_NAME);

        assertThat(commit.getStatsStatus()).isEqualTo(StatsStatus.PENDING);
    }

    // ── proactive throttle: remaining < 100, reset in past → no real sleep ─

    @Test
    void enrichSingle_rateLimitNearlyExhaustedWithPastReset_noRealSleep() {
        // resetAt in the past → waitMs = max(0, past - now) = 0 → Thread.sleep(0)
        long pastEpoch = Instant.now().getEpochSecond() - 60;
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-RateLimit-Remaining", "50")
                        .withHeader("X-RateLimit-Reset", String.valueOf(pastEpoch))
                        .withBody("""
                                {"stats":{"additions":1,"deletions":0},"files":[]}""")));

        GitCommitEntity commit = commit(0);
        service.enrichSingle(commit, wmBaseUrl, "token", REPO_NAME);

        assertThat(commit.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
        assertThat(commit.getFilesChanged()).isEqualTo(0);
    }

    // ── 429 → Retry-After: 0 → succeeds on retry ───────────────────────────

    @Test
    void enrichSingle_429WithRetryAfterZero_retriesAndSucceeds() {
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .inScenario("backoff")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("retried"));

        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .inScenario("backoff")
                .whenScenarioStateIs("retried")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"stats":{"additions":5,"deletions":2},
                                 "files":[{"filename":"x.py"}]}""")));

        GitCommitEntity commit = commit(0);
        service.enrichSingle(commit, wmBaseUrl, "token", REPO_NAME);

        assertThat(commit.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
        assertThat(commit.getAdditions()).isEqualTo(5);
    }

    // ── processPendingBatchForRepo: empty batch → returns 0 ────────────────

    @Test
    void processPendingBatchForRepo_emptyBatch_returnsZero() {
        when(commitRepository.findPendingCommitsForRepository(
                eq(StatsStatus.PENDING), eq(10L), any(PageRequest.class)))
                .thenReturn(List.of());

        int result = service.processPendingBatchForRepo(wmBaseUrl, "token", REPO_NAME, 10L);

        assertThat(result).isEqualTo(0);
        verify(commitRepository, never()).saveAll(any());
    }

    // ── processPendingBatchForRepo: enriches commits and returns count ──────

    @Test
    void processPendingBatchForRepo_withPendingCommit_enrichesAndReturnsCount() {
        GitCommitEntity c = commit(0);
        when(commitRepository.findPendingCommitsForRepository(
                eq(StatsStatus.PENDING), eq(10L), any(PageRequest.class)))
                .thenReturn(List.of(c));
        when(commitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/commits/" + SHA))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"stats":{"additions":3,"deletions":1},
                                 "files":[{"filename":"Main.java"}]}""")));

        int result = service.processPendingBatchForRepo(wmBaseUrl, "token", REPO_NAME, 10L);

        assertThat(result).isEqualTo(1);
        verify(commitRepository).saveAll(List.of(c));
        assertThat(c.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
    }

    // ── enrichImmediate: empty list → does nothing ──────────────────────────

    @Test
    void enrichImmediate_emptyList_doesNothing() {
        service.enrichImmediate(List.of(), wmBaseUrl, "token", REPO_NAME);
        verifyNoInteractions(commitRepository);
    }
}
