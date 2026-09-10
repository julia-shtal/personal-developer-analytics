package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPrReviewEntity;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHUser;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for identity capture on reviews and GitHub issues — the two record types the
 * first ingest-mapping pass did not cover directly.
 *
 * <p>{@code reviewer_github_id} is what review participation counts on and what the self-review
 * exclusion compares, and the creator/assignee ids are what give issue metrics an author filter
 * at all.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WireMockTest
class ReviewAndIssueIdentityMappingTest {

    @Mock GitHubPullRequestRepository prRepository;
    @Mock GitHubPrReviewRepository reviewRepository;
    @Mock GitHubClientFactory clientFactory;
    @Mock IssueRepository issueRepository;
    @Mock GitRepositoryEntityRepository gitRepositoryEntityRepository;

    GitHubPrStatsEnrichmentService enrichment;
    GitHubIssuesCollector issuesCollector;
    String wmBaseUrl;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        wmBaseUrl = wm.getHttpBaseUrl();
        enrichment = new GitHubPrStatsEnrichmentService(prRepository, reviewRepository, new ObjectMapper());
        issuesCollector = new GitHubIssuesCollector(clientFactory, issueRepository, gitRepositoryEntityRepository);
    }

    // ── reviews ─────────────────────────────────────────────────────────────

    @Test
    void refreshReviews_reviewWithUser_storesReviewerGithubIdBesideLogin() throws Exception {
        GitHubPullRequestEntity pr = pr(42);
        stubReviews("""
            [{"state": "APPROVED", "submitted_at": "2026-03-02T10:00:00Z",
              "user": {"id": 202, "login": "reviewer"}}]
            """);

        enrichment.refreshReviews(pr, wmBaseUrl, "ghp_token", "owner/test-repo");

        GitHubPrReviewEntity review = capturedReviews().get(0);
        assertThat(review.getReviewerGithubId()).isEqualTo(202L);
        assertThat(review.getReviewerLogin()).isEqualTo("reviewer");
    }

    @Test
    void refreshReviews_reviewWithNullUser_storesNullReviewerGithubId() throws Exception {
        GitHubPullRequestEntity pr = pr(42);
        stubReviews("""
            [{"state": "APPROVED", "submitted_at": "2026-03-02T10:00:00Z", "user": null}]
            """);

        enrichment.refreshReviews(pr, wmBaseUrl, "ghp_token", "owner/test-repo");

        // A deleted reviewer account resolves to nothing; the review is kept but attributed
        // to nobody, which is preferable to attributing it by a stale login.
        assertThat(capturedReviews().get(0).getReviewerGithubId()).isNull();
    }

    @Test
    void refreshReviews_existingRows_areReplacedNotAppended() throws Exception {
        GitHubPullRequestEntity pr = pr(42);
        stubReviews("""
            [{"state": "APPROVED", "submitted_at": "2026-03-02T10:00:00Z",
              "user": {"id": 202, "login": "reviewer"}}]
            """);

        enrichment.refreshReviews(pr, wmBaseUrl, "ghp_token", "owner/test-repo");

        // The backfill relies on wholesale replacement: rows written before the id column
        // existed have to disappear, not sit alongside the new ones.
        InOrderHelper.assertDeleteBeforeSave(reviewRepository, pr);
    }

    @Test
    void refreshReviews_reviewWithoutSubmittedAt_isSkipped() throws Exception {
        GitHubPullRequestEntity pr = pr(42);
        stubReviews("""
            [{"state": "PENDING", "submitted_at": null, "user": {"id": 202, "login": "reviewer"}}]
            """);

        enrichment.refreshReviews(pr, wmBaseUrl, "ghp_token", "owner/test-repo");

        // A pending (unsubmitted) review has no timestamp to place it in any window.
        verify(reviewRepository, never()).saveAll(any());
    }

    // ── GitHub issues ───────────────────────────────────────────────────────

    @Test
    void buildIssueEntity_issueWithCreatorAndAssignee_storesBothAccountIds() throws Exception {
        IssueEntity issue = buildIssue(ghUser(101L, "creator"), ghUser(202L, "assignee"));

        assertThat(issue.getCreatorGithubId()).isEqualTo(101L);
        assertThat(issue.getAssigneeGithubId()).isEqualTo(202L);
        // Logins remain for display.
        assertThat(issue.getCreator()).isEqualTo("creator");
        assertThat(issue.getAssignee()).isEqualTo("assignee");
    }

    @Test
    void buildIssueEntity_unassignedIssue_storesNullAssigneeId() throws Exception {
        IssueEntity issue = buildIssue(ghUser(101L, "creator"), null);

        assertThat(issue.getCreatorGithubId()).isEqualTo(101L);
        // Unassigned issues count for nobody's DAILY_ISSUES_CLOSED, which is correct: that
        // metric follows the assignee.
        assertThat(issue.getAssigneeGithubId()).isNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private GitHubPullRequestEntity pr(int number) {
        GitHubPullRequestEntity pr = new GitHubPullRequestEntity();
        pr.setNumber(number);
        return pr;
    }

    private void stubReviews(String body) {
        stubFor(get(urlPathMatching("/repos/owner/test-repo/pulls/42/reviews.*"))
                .willReturn(okJson(body)));
    }

    private List<GitHubPrReviewEntity> capturedReviews() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GitHubPrReviewEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private GHUser ghUser(long id, String login) throws Exception {
        GHUser user = mock(GHUser.class);
        when(user.getId()).thenReturn(id);
        when(user.getLogin()).thenReturn(login);
        return user;
    }

    private IssueEntity buildIssue(GHUser creator, GHUser assignee) throws Exception {
        GHIssue gh = mock(GHIssue.class);
        when(gh.getNumber()).thenReturn(7);
        when(gh.getTitle()).thenReturn("An issue");
        when(gh.getState()).thenReturn(GHIssueState.OPEN);
        when(gh.getUser()).thenReturn(creator);
        when(gh.getAssignee()).thenReturn(assignee);
        when(gh.getLabels()).thenReturn(List.of());

        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(1L);
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(10L);
        repo.setRepoFullName("owner/test-repo");

        when(issueRepository.findByDataSourceAndSourceIssueKey(any(), any())).thenReturn(Optional.empty());

        return issuesCollector.buildIssueEntity(cfg, repo, gh);
    }

    /** Keeps the delete-then-save ordering assertion out of the test body. */
    private static final class InOrderHelper {
        static void assertDeleteBeforeSave(GitHubPrReviewRepository repo, GitHubPullRequestEntity pr) {
            org.mockito.InOrder order = inOrder(repo);
            order.verify(repo).deleteAllByPullRequest(pr);
            order.verify(repo).saveAll(any());
        }
    }
}
