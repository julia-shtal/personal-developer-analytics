package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.model.StatsSkipReason;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageProjection;
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
 * Acceptance tests for the stats-coverage grouping. Run against the real schema because the
 * grouping key includes a nullable enum column, and SQL's treatment of NULL in GROUP BY is the
 * thing being pinned: COMPLETE rows must fold into one group, not vanish.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StatsCoverageQueryTest {

    @Autowired GitCommitEntityRepository commitRepository;
    @Autowired GitHubPullRequestRepository pullRequestRepository;
    @Autowired JdbcTemplate jdbc;

    private static final Instant WHEN = Instant.parse("2026-03-02T10:00:00Z");
    private static final Instant OUTSIDE = Instant.parse("2026-04-02T10:00:00Z");
    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO   = Instant.parse("2026-03-08T00:00:00Z");

    private Long repoId;

    /** git_commits.hash is globally UNIQUE and nanoTime repeats on a coarse clock. */
    private int seq;

    @BeforeEach
    void seed() {
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, 'x') RETURNING id",
                Long.class,
                "coverage-q-user-" + System.nanoTime(),
                "coverage-q-" + System.nanoTime() + "@coverage-test.example");

        Long dataSourceId = jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, path) "
                        + "VALUES (?, 'GIT_LOCAL', ?, '/tmp/coverage-q-ds') RETURNING id",
                Long.class, userId, "coverage-q-ds-" + System.nanoTime());

        String name = "coverage-q-repo-" + System.nanoTime();
        repoId = jdbc.queryForObject(
                "INSERT INTO git_repositories (data_source_id, name, local_path, repo_type) "
                        + "VALUES (?, ?, ?, 'LOCAL') RETURNING id",
                Long.class, dataSourceId, name, "/tmp/" + name);

        insertCommit(WHEN, "COMPLETE", null);
        insertCommit(WHEN, "COMPLETE", null);
        insertCommit(WHEN, "COMPLETE", null);
        insertCommit(WHEN, "SKIPPED", "DIFF_TOO_LARGE");
        insertCommit(WHEN, "SKIPPED", "RECORD_UNAVAILABLE");
        insertCommit(WHEN, "SKIPPED", "UNKNOWN");
        insertCommit(WHEN, "PENDING", null);
        // Outside the window: must not appear in any group.
        insertCommit(OUTSIDE, "SKIPPED", "DIFF_TOO_LARGE");
    }

    @Test
    void countByStatsStateInRange_skippedCommits_areSplitByReason() {
        List<StatsCoverageProjection> rows =
                commitRepository.countByStatsStateInRange(List.of(repoId), FROM, TO);

        assertThat(countOf(rows, StatsStatus.SKIPPED, StatsSkipReason.DIFF_TOO_LARGE)).isEqualTo(1);
        assertThat(countOf(rows, StatsStatus.SKIPPED, StatsSkipReason.RECORD_UNAVAILABLE)).isEqualTo(1);
        assertThat(countOf(rows, StatsStatus.SKIPPED, StatsSkipReason.UNKNOWN)).isEqualTo(1);
    }

    @Test
    void countByStatsStateInRange_nullReasonRows_foldIntoOneGroupPerStatus() {
        List<StatsCoverageProjection> rows =
                commitRepository.countByStatsStateInRange(List.of(repoId), FROM, TO);

        assertThat(countOf(rows, StatsStatus.COMPLETE, null)).isEqualTo(3);
        assertThat(countOf(rows, StatsStatus.PENDING, null)).isEqualTo(1);
    }

    @Test
    void countByStatsStateInRange_groupsSumToRecordsInRange() {
        List<StatsCoverageProjection> rows =
                commitRepository.countByStatsStateInRange(List.of(repoId), FROM, TO);

        // Seven of the eight seeded commits are inside the window.
        assertThat(rows.stream().mapToLong(StatsCoverageProjection::getRecordCount).sum()).isEqualTo(7);
    }

    @Test
    void countByStatsStateInRange_repoNotInScope_returnsNothing() {
        assertThat(commitRepository.countByStatsStateInRange(List.of(-1L), FROM, TO)).isEmpty();
    }

    @Test
    void countByStatsStateInRange_pullRequests_areSplitByReason() {
        insertPullRequest(1, WHEN, "COMPLETE", null);
        insertPullRequest(2, WHEN, "SKIPPED", "DIFF_TOO_LARGE");
        insertPullRequest(3, WHEN, "SKIPPED", "RECORD_UNAVAILABLE");
        insertPullRequest(4, OUTSIDE, "SKIPPED", "RECORD_UNAVAILABLE");

        List<StatsCoverageProjection> rows =
                pullRequestRepository.countByStatsStateInRange(List.of(repoId), FROM, TO);

        assertThat(countOf(rows, StatsStatus.COMPLETE, null)).isEqualTo(1);
        assertThat(countOf(rows, StatsStatus.SKIPPED, StatsSkipReason.DIFF_TOO_LARGE)).isEqualTo(1);
        assertThat(countOf(rows, StatsStatus.SKIPPED, StatsSkipReason.RECORD_UNAVAILABLE)).isEqualTo(1);
        assertThat(rows.stream().mapToLong(StatsCoverageProjection::getRecordCount).sum()).isEqualTo(3);
    }

    @Test
    void skipReasonIndex_isUsedForTheSkippedBreakdown() {
        // Pins the partial index as reachable, not merely present: a sequential scan here would
        // still return the right answer, so only the plan can tell the two apart.
        // SET LOCAL, not SET: a plain SET outlives the rollback on the pooled connection and
        // would silently change the plan for every later test in the run.
        jdbc.execute("SET LOCAL enable_seqscan = off");
        String plan = String.join(" ", jdbc.queryForList(
                "EXPLAIN SELECT stats_skip_reason, COUNT(*) FROM git_commits "
                        + "WHERE repository_id = " + repoId + " AND stats_status = 'SKIPPED' "
                        + "GROUP BY stats_skip_reason",
                String.class));

        assertThat(plan).contains("ix_git_commits_skip_reason");
    }

    private long countOf(List<StatsCoverageProjection> rows, StatsStatus status, StatsSkipReason reason) {
        return rows.stream()
                .filter(r -> r.getStatsStatus() == status && r.getStatsSkipReason() == reason)
                .mapToLong(StatsCoverageProjection::getRecordCount)
                .sum();
    }

    // -------------------------------------------------------------------------
    // Fixtures. Inserted with JdbcTemplate so the test states the exact column values the
    // queries read. author_date and created_at are TIMESTAMP WITHOUT TIME ZONE, so Instant
    // fixtures are bound as UTC LocalDateTime rather than through the JVM zone.
    // -------------------------------------------------------------------------

    private void insertCommit(Instant when, String statsStatus, String skipReason) {
        jdbc.update(
                "INSERT INTO git_commits (repository_id, hash, author_name, author_email, "
                        + "author_date, message, additions, deletions, stats_status, stats_skip_reason) "
                        + "VALUES (?, ?, 'Fixture', 'fixture@x.org', ?, 'msg', 5, 5, ?, ?)",
                repoId,
                "coverage-" + System.nanoTime() + "-" + (++seq),
                LocalDateTime.ofInstant(when, ZoneOffset.UTC),
                statsStatus,
                skipReason);
    }

    private void insertPullRequest(int number, Instant when, String statsStatus, String skipReason) {
        jdbc.update(
                "INSERT INTO github_pull_requests (repository_id, number, title, state, merged, "
                        + "created_at, stats_status, stats_skip_reason) "
                        + "VALUES (?, ?, 'Fixture PR', 'closed', false, ?, ?, ?)",
                repoId,
                number,
                LocalDateTime.ofInstant(when, ZoneOffset.UTC),
                statsStatus,
                skipReason);
    }
}
