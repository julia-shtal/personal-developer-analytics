package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
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
class GitHubPrStatsEnrichmentServiceTest {

    @Mock GitHubPullRequestRepository prRepository;
    @Mock GitHubPrReviewRepository reviewRepository;

    GitHubPrStatsEnrichmentService service;
    String wmBaseUrl;

    private static final String REPO_NAME = "owner/test-repo";

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        service = new GitHubPrStatsEnrichmentService(prRepository, reviewRepository, new ObjectMapper());
        wmBaseUrl = wm.getHttpBaseUrl();
    }

    private GitHubPullRequestEntity pr(int number, int attempts) {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(10L);
        repo.setRepoFullName(REPO_NAME);

        GitHubPullRequestEntity p = new GitHubPullRequestEntity();
        p.setId(1L);
        p.setNumber(number);
        p.setRepository(repo);
        p.setStatsAttempts(attempts);
        p.setStatsStatus(StatsStatus.PENDING);
        return p;
    }

    /** Stubs the reviews endpoint to return an empty array (no reviews). */
    private void stubReviewsEmpty(int prNumber) {
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/" + prNumber + "/reviews"))
                .willReturn(aResponse().withStatus(200).withBody("[]")
                        .withHeader("Content-Type", "application/json")));
    }

    // ── max attempts exceeded → FAILED ─────────────────────────────────────

    @Test
    void enrichSingle_maxAttemptsExceeded_marksFailed() {
        // MAX_ATTEMPTS = 3; after increment: 3+1=4 > 3 → FAILED
        GitHubPullRequestEntity pr = pr(1, 3);

        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        assertThat(pr.getStatsAttempts()).isEqualTo(4);
        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.FAILED);
        assertThat(pr.getStatsFetchedAt()).isNotNull();
    }

    // ── 200 response → COMPLETE with stats ─────────────────────────────────

    @Test
    void enrichSingle_successResponse_marksCompleteWithStats() {
        stubReviewsEmpty(42);
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/42"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"additions":15,"deletions":5,"changed_files":3,"commits":2}""")));

        GitHubPullRequestEntity pr = pr(42, 0);
        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
        assertThat(pr.getAdditions()).isEqualTo(15);
        assertThat(pr.getDeletions()).isEqualTo(5);
        assertThat(pr.getChangedFiles()).isEqualTo(3);
        assertThat(pr.getCommitsCount()).isEqualTo(2);
        assertThat(pr.getStatsFetchedAt()).isNotNull();
    }

    // ── reviews fetched and persisted ──────────────────────────────────────

    @Test
    void enrichSingle_withReviews_deletesOldAndSavesNew() {
        String reviewBody = """
                [{"user":{"login":"reviewer1"},"state":"APPROVED",
                  "submitted_at":"2024-01-01T10:00:00Z"},
                 {"user":{"login":"reviewer2"},"state":"CHANGES_REQUESTED",
                  "submitted_at":"2024-01-01T11:00:00Z"}]""";
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/5/reviews"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(reviewBody)));
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/5"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"additions":1,"deletions":0,"changed_files":1,"commits":1}""")));

        GitHubPullRequestEntity pr = pr(5, 0);
        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        verify(reviewRepository).deleteAllByPullRequest(pr);
        verify(reviewRepository).saveAll(argThat(reviews -> ((List<?>) reviews).size() == 2));
        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
    }

    // ── 403 too large → SKIPPED ────────────────────────────────────────────

    @Test
    void enrichSingle_403TooLarge_marksSkipped() {
        stubReviewsEmpty(10);
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/10"))
                .willReturn(aResponse().withStatus(403)
                        .withBody("diff is too large to process")));

        GitHubPullRequestEntity pr = pr(10, 0);
        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.SKIPPED);
        assertThat(pr.getStatsFetchedAt()).isNotNull();
    }

    // ── 403 secondary rate limit → leave PENDING ───────────────────────────

    @Test
    void enrichSingle_403SecondaryRateLimit_leavesPending() {
        stubReviewsEmpty(11);
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/11"))
                .willReturn(aResponse().withStatus(403)
                        .withBody("secondary rate limit triggered")));

        GitHubPullRequestEntity pr = pr(11, 0);
        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.PENDING);
        assertThat(pr.getStatsFetchedAt()).isNull();
    }

    // ── 404 → SKIPPED ──────────────────────────────────────────────────────

    @Test
    void enrichSingle_404_marksSkipped() {
        stubReviewsEmpty(20);
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/20"))
                .willReturn(aResponse().withStatus(404)));

        GitHubPullRequestEntity pr = pr(20, 0);
        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.SKIPPED);
    }

    // ── 422 → SKIPPED ──────────────────────────────────────────────────────

    @Test
    void enrichSingle_422_marksSkipped() {
        stubReviewsEmpty(21);
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/21"))
                .willReturn(aResponse().withStatus(422)));

        GitHubPullRequestEntity pr = pr(21, 0);
        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.SKIPPED);
    }

    // ── unexpected status → stays PENDING ──────────────────────────────────

    @Test
    void enrichSingle_unexpectedStatus_leavesPending() {
        stubReviewsEmpty(22);
        // Retry-After: 0 avoids the 5-second INITIAL_BACKOFF_MS sleep on 5xx.
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/22"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Retry-After", "0")
                        .withBody("server error")));

        GitHubPullRequestEntity pr = pr(22, 0);
        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.PENDING);
    }

    // ── proactive throttle: reset in past → no real sleep ──────────────────

    @Test
    void enrichSingle_rateLimitNearlyExhaustedWithPastReset_noRealSleep() {
        stubReviewsEmpty(30);
        long pastEpoch = Instant.now().getEpochSecond() - 60;
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/30"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-RateLimit-Remaining", "50")
                        .withHeader("X-RateLimit-Reset", String.valueOf(pastEpoch))
                        .withBody("""
                                {"additions":1,"deletions":0,"changed_files":1,"commits":1}""")));

        GitHubPullRequestEntity pr = pr(30, 0);
        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
    }

    // ── 429 → Retry-After: 0 → succeeds on retry ───────────────────────────

    @Test
    void enrichSingle_429WithRetryAfterZero_retriesAndSucceeds() {
        stubReviewsEmpty(31);

        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/31"))
                .inScenario("backoff")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("retried"));

        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/31"))
                .inScenario("backoff")
                .whenScenarioStateIs("retried")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"additions":3,"deletions":2,"changed_files":2,"commits":1}""")));

        GitHubPullRequestEntity pr = pr(31, 0);
        service.enrichSingle(pr, wmBaseUrl, "token", REPO_NAME);

        assertThat(pr.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
        assertThat(pr.getAdditions()).isEqualTo(3);
    }

    // ── processPendingBatchForRepo: empty batch → returns 0 ────────────────

    @Test
    void processPendingBatchForRepo_emptyBatch_returnsZero() {
        when(prRepository.findPendingPrsForRepository(
                eq(StatsStatus.PENDING), eq(10L), any(PageRequest.class)))
                .thenReturn(List.of());

        int result = service.processPendingBatchForRepo(wmBaseUrl, "token", REPO_NAME, 10L);

        assertThat(result).isEqualTo(0);
        verify(prRepository, never()).saveAll(any());
    }

    // ── processPendingBatchForRepo: enriches batch and returns count ────────

    @Test
    void processPendingBatchForRepo_withPendingPr_enrichesAndReturnsCount() {
        GitHubPullRequestEntity p = pr(99, 0);
        when(prRepository.findPendingPrsForRepository(
                eq(StatsStatus.PENDING), eq(10L), any(PageRequest.class)))
                .thenReturn(List.of(p));
        when(prRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        stubReviewsEmpty(99);
        stubFor(get(urlPathEqualTo("/repos/owner/test-repo/pulls/99"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"additions":7,"deletions":3,"changed_files":2,"commits":4}""")));

        int result = service.processPendingBatchForRepo(wmBaseUrl, "token", REPO_NAME, 10L);

        assertThat(result).isEqualTo(1);
        verify(prRepository).saveAll(List.of(p));
        assertThat(p.getStatsStatus()).isEqualTo(StatsStatus.COMPLETE);
    }

    // ── enrichImmediate: empty list → does nothing ──────────────────────────

    @Test
    void enrichImmediate_emptyList_doesNothing() {
        service.enrichImmediate(List.of(), wmBaseUrl, "token", REPO_NAME);
        verifyNoInteractions(prRepository);
        verifyNoInteractions(reviewRepository);
    }
}
