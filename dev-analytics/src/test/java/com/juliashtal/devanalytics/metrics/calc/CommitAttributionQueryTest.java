package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.metrics.model.DailyCommitsProjection;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Acceptance tests for commit attribution.
 *
 * <p>The metric this guards is the one the change exists for: matching commits against the single
 * account email dropped 99 of 276 commits (36%) on the reference installation, because commits made
 * through the GitHub web UI carry a {@code users.noreply.github.com} alias rather than the address
 * on the profile.
 *
 * <p>Run against the real schema rather than mocks. The predicate is a disjunction over a nullable
 * bigint and a {@code lower()} expression, and its whole point is how SQL evaluates it: a mocked
 * repository would assert only that the calculator passed the arguments it was told to.
 *
 * <p>Fixture conventions follow {@code EarliestActivityQueryTest} — JdbcTemplate inserts stating the
 * exact column values, and {@code author_date} bound as a UTC {@link LocalDateTime} because the
 * column is still {@code TIMESTAMP WITHOUT TIME ZONE}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommitAttributionQueryTest {

    @Autowired GitCommitEntityRepository commitRepository;
    @Autowired JdbcTemplate jdbc;

    private static final Instant WHEN = Instant.parse("2026-03-02T10:00:00Z");
    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO   = Instant.parse("2026-03-03T00:00:00Z");

    /** User A is linked to GitHub account 101 and declares one address. */
    private static final long   A_GITHUB_ID = 101L;
    private static final String A_EMAIL     = "a@x.org";
    /** User B declares an address but has never linked a GitHub account. */
    private static final String B_EMAIL     = "b@x.org";

    private Long repoId;

    @BeforeEach
    void seed() {
        Long userId = insertUser();
        Long dataSourceId = insertDataSource(userId);
        repoId = insertRepo(dataSourceId, "attribution-fixture-" + System.nanoTime());

        // 1. Collected from GitHub with an address A never declared -- the noreply-alias case.
        //    Only author_github_id can attribute this one.
        insertCommit("c-gh-undeclared", "49405289+a@users.noreply.github.com", A_GITHUB_ID);

        // 2. A local JGit commit, and deliberately in different case from the declared address.
        //    Local commits never carry a GitHub id, so only the email path can match it, and it
        //    matches only because both sides are lower-cased.
        insertCommit("c-local-a-uppercase", "A@X.org", null);

        // 3. A local commit by B.
        insertCommit("c-local-b", B_EMAIL, null);

        // 4. A local commit by someone else entirely.
        insertCommit("c-local-stranger", "stranger@x.org", null);

        // 5. A GitHub commit whose email GitHub could not resolve to any account.
        insertCommit("c-gh-unresolved", "stranger@x.org", null);
    }

    @Test
    void aggregateCommitsDailyByRepoIdsAndIdentity_userWithIdAndEmail_countsBothPathsAndNoStrangers() {
        assertThat(commitsFor(A_GITHUB_ID, Set.of(A_EMAIL))).isEqualTo(2);
    }

    @Test
    void aggregateCommitsDailyByRepoIdsAndIdentity_userWithEmailOnly_countsOnlyTheirOwnCommit() {
        assertThat(commitsFor(null, Set.of(B_EMAIL))).isEqualTo(1);
    }

    @Test
    void aggregateCommitsDailyByRepoIdsAndIdentity_strangerCommits_areNeverAttributed() {
        // Both stranger commits are in the repo scope and inside the window; the only thing
        // keeping them out of anyone's metrics is the identity predicate.
        long attributedInTotal = commitsFor(A_GITHUB_ID, Set.of(A_EMAIL)) + commitsFor(null, Set.of(B_EMAIL));
        assertThat(attributedInTotal).isEqualTo(3);
    }

    @Test
    void aggregateCommitsDailyByRepoIdsAndIdentity_githubIdButNoDeclaredEmails_matchesOnIdWithoutSqlError() {
        // Hibernate renders an empty collection parameter as `in ()`, which PostgreSQL rejects,
        // so CalcUtils.emailsOrSentinel substitutes an unmatchable value. Both halves matter and
        // both have failed here: the clause has to be valid SQL *and* match nothing, leaving the
        // author_github_id branch to return the one GitHub-linked commit on its own.
        assertThatCode(() -> commitsFor(A_GITHUB_ID, Set.of())).doesNotThrowAnyException();
        assertThat(commitsFor(A_GITHUB_ID, Set.of())).isEqualTo(1);
    }

    @Test
    void aggregateCommitsDailyByRepoIdsAndIdentity_noIdentityAtAll_countsNothing() {
        assertThat(commitsFor(null, Set.of())).isZero();
    }

    @Test
    void aggregateCommitsDailyByRepoIdsAndIdentity_commitMatchingBothPaths_isCountedOnce() {
        // Same row satisfies the id branch and the email branch. A disjunction selects it once;
        // an implementation that unioned two queries would double it.
        insertCommit("c-both-paths", A_EMAIL, A_GITHUB_ID);

        assertThat(commitsFor(A_GITHUB_ID, Set.of(A_EMAIL))).isEqualTo(3);
    }

    @Test
    void aggregateCommitsDailyByRepoIdsAndIdentity_anotherUsersGithubId_doesNotLeakCommits() {
        // Guards the ownership rule from the query side: holding a different account id must not
        // pick up commits attributed to 101.
        assertThat(commitsFor(999L, Set.of("nobody@x.org"))).isZero();
    }

    /** Total commits the identity matches, summed across the per-day rows the projection returns. */
    private long commitsFor(Long githubUserId, Set<String> emails) {
        List<DailyCommitsProjection> rows = commitRepository.aggregateCommitsDailyByRepoIdsAndIdentity(
                List.of(repoId), githubUserId, CalcUtils.emailsOrSentinel(emails), FROM, TO);
        return rows.stream().mapToLong(DailyCommitsProjection::getCommitsCount).sum();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "commit-attr-user-" + System.nanoTime(),
                "commit-attr-" + System.nanoTime() + "@attribution-test.example",
                "fixture-hash");
    }

    private Long insertDataSource(Long userId) {
        return jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, path) "
                        + "VALUES (?, 'GIT_LOCAL', ?, ?) RETURNING id",
                Long.class, userId, "commit-attr-ds-" + System.nanoTime(), "/tmp/commit-attr-ds");
    }

    private Long insertRepo(Long dataSourceId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO git_repositories (data_source_id, name, local_path, repo_type) "
                        + "VALUES (?, ?, ?, 'LOCAL') RETURNING id",
                Long.class, dataSourceId, name, "/tmp/" + name);
    }

    /** Hashes are globally unique, so each carries the test's nanotime suffix. */
    private void insertCommit(String hashPrefix, String authorEmail, Long authorGithubId) {
        jdbc.update(
                "INSERT INTO git_commits (repository_id, hash, author_name, author_email, "
                        + "author_github_id, author_date, message, additions, deletions, stats_status) "
                        + "VALUES (?, ?, 'Fixture', ?, ?, ?, 'msg', 5, 5, 'COMPLETE')",
                repoId,
                hashPrefix + "-" + System.nanoTime(),
                authorEmail,
                authorGithubId,
                LocalDateTime.ofInstant(WHEN, ZoneOffset.UTC));
    }
}
