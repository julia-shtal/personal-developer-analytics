package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.DailyCountProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for PR and review attribution — the rename-safety cases.
 *
 * <p>Attribution compares numeric account ids, so a rename cannot split one person's history and
 * differing spellings cannot defeat the self-review exclusion. The timestamp columns are
 * {@code TIMESTAMP WITHOUT TIME ZONE}, so fixtures bind UTC {@link LocalDateTime} — see
 * {@code EarliestActivityQueryTest}.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PrAttributionQueryTest {

    @Autowired GitHubPullRequestRepository prRepository;
    @Autowired GitHubPrReviewRepository reviewRepository;
    @Autowired JdbcTemplate jdbc;

    private static final Instant WHEN = Instant.parse("2026-03-02T10:00:00Z");
    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO   = Instant.parse("2026-03-03T00:00:00Z");

    /** The user renamed themselves on GitHub: stored rows still say "old-name". */
    private static final long   AUTHOR_ID    = 101L;
    private static final String STORED_LOGIN = "old-name";
    private static final long   REVIEWER_ID  = 202L;

    private Long repoId;
    private Long selfReviewedPrId;
    private Long peerReviewedPrId;

    @BeforeEach
    void seed() {
        Long userId = insertUser();
        Long dataSourceId = insertDataSource(userId);
        repoId = insertRepo(dataSourceId, "pr-attr-" + System.nanoTime());

        selfReviewedPrId = insertPr(1, STORED_LOGIN, AUTHOR_ID);
        peerReviewedPrId = insertPr(2, "someone-else", REVIEWER_ID);
    }

    @Test
    void aggregatePrCreatedDailyByRepoIdsAndAuthorGithubId_loginRenamedSinceCollection_stillMatches() {
        // The profile now says "new-name"; nothing in the database does. Only the id connects them.
        long created = prRepository
                .aggregatePrCreatedDailyByRepoIdsAndAuthorGithubId(List.of(repoId), AUTHOR_ID, FROM, TO)
                .stream().mapToLong(DailyCountProjection::getCount).sum();

        assertThat(created).isEqualTo(1);
    }

    @Test
    void aggregatePrCreatedDailyByRepoIdsAndAuthorGithubId_unlinkedAccount_matchesNothing() {
        long created = prRepository
                .aggregatePrCreatedDailyByRepoIdsAndAuthorGithubId(List.of(repoId), 999L, FROM, TO)
                .stream().mapToLong(DailyCountProjection::getCount).sum();

        assertThat(created).isZero();
    }

    @Test
    void countDistinctPrsReviewedByUser_selfReviewSpelledDifferently_isExcluded() {
        // Account 101 reviewing its own PR, with the two rows spelling the login differently:
        // a login comparison would see two people and count the self-review.
        insertReview(selfReviewedPrId, "new-name", AUTHOR_ID);

        assertThat(reviewRepository.countDistinctPrsReviewedByUser(AUTHOR_ID, List.of(repoId), FROM, TO))
                .isZero();
    }

    @Test
    void countDistinctPrsReviewedByUser_reviewOfSomeoneElsesPr_isCounted() {
        insertReview(selfReviewedPrId, "reviewer", REVIEWER_ID);

        assertThat(reviewRepository.countDistinctPrsReviewedByUser(REVIEWER_ID, List.of(repoId), FROM, TO))
                .isEqualTo(1);
    }

    @Test
    void countDistinctPrsReviewedByUser_severalReviewsOnOnePr_countsThePrOnce() {
        insertReview(selfReviewedPrId, "reviewer", REVIEWER_ID);
        insertReview(selfReviewedPrId, "reviewer", REVIEWER_ID);

        assertThat(reviewRepository.countDistinctPrsReviewedByUser(REVIEWER_ID, List.of(repoId), FROM, TO))
                .isEqualTo(1);
    }

    @Test
    void countDistinctPrsReviewedByUser_mixOfSelfAndPeerReviews_countsOnlyThePeerReview() {
        insertReview(peerReviewedPrId, "reviewer", REVIEWER_ID);   // self-review: PR 2 is theirs
        insertReview(selfReviewedPrId, "reviewer", REVIEWER_ID);   // peer review: PR 1 is 101's

        assertThat(reviewRepository.countDistinctPrsReviewedByUser(REVIEWER_ID, List.of(repoId), FROM, TO))
                .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "pr-attr-user-" + System.nanoTime(),
                "pr-attr-" + System.nanoTime() + "@attribution-test.example",
                "fixture-hash");
    }

    private Long insertDataSource(Long userId) {
        return jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, path) "
                        + "VALUES (?, 'GIT_LOCAL', ?, ?) RETURNING id",
                Long.class, userId, "pr-attr-ds-" + System.nanoTime(), "/tmp/pr-attr-ds");
    }

    private Long insertRepo(Long dataSourceId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO git_repositories (data_source_id, name, local_path, repo_type) "
                        + "VALUES (?, ?, ?, 'LOCAL') RETURNING id",
                Long.class, dataSourceId, name, "/tmp/" + name);
    }

    private Long insertPr(int number, String authorLogin, Long authorGithubId) {
        return jdbc.queryForObject(
                "INSERT INTO github_pull_requests (repository_id, number, title, author_login, "
                        + "author_github_id, state, merged, created_at) "
                        + "VALUES (?, ?, 'PR', ?, ?, 'closed', false, ?) RETURNING id",
                Long.class, repoId, number, authorLogin, authorGithubId,
                LocalDateTime.ofInstant(WHEN, ZoneOffset.UTC));
    }

    private void insertReview(Long prId, String reviewerLogin, Long reviewerGithubId) {
        jdbc.update(
                "INSERT INTO github_pr_reviews (pr_id, reviewer_login, reviewer_github_id, state, submitted_at) "
                        + "VALUES (?, ?, ?, 'APPROVED', ?)",
                prId, reviewerLogin, reviewerGithubId,
                LocalDateTime.ofInstant(WHEN, ZoneOffset.UTC));
    }
}
