package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.metrics.model.DailyChurnProjection;
import com.juliashtal.devanalytics.metrics.model.DailyCommitsProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which commits enter the two line-count populations, and which only enter the count.
 *
 * <p>Run against the real schema because the split is expressed in SQL — a conditional
 * aggregate in one query, a where clause in the other — and the difference between those
 * two shapes is what a mock cannot show.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EnrichmentPopulationQueryTest {

    @Autowired GitCommitEntityRepository commitRepository;
    @Autowired JdbcTemplate jdbc;

    private static final Instant DAY_ONE = Instant.parse("2026-03-02T10:00:00Z");
    private static final Instant DAY_TWO = Instant.parse("2026-03-03T10:00:00Z");
    private static final Instant FROM    = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO      = Instant.parse("2026-03-05T00:00:00Z");

    private static final String EMAIL = "author@x.org";

    private Long repoId;

    @BeforeEach
    void seed() {
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "enrichment-user-" + System.nanoTime(),
                "enrichment-" + System.nanoTime() + "@population-test.example",
                "fixture-hash");
        Long dataSourceId = jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, path) "
                        + "VALUES (?, 'GIT_LOCAL', ?, ?) RETURNING id",
                Long.class, userId, "enrichment-ds-" + System.nanoTime(), "/tmp/enrichment-ds");
        String name = "enrichment-fixture-" + System.nanoTime();
        repoId = jdbc.queryForObject(
                "INSERT INTO git_repositories (data_source_id, name, local_path, repo_type) "
                        + "VALUES (?, ?, ?, 'LOCAL') RETURNING id",
                Long.class, dataSourceId, name, "/tmp/" + name);
    }

    @Test
    void aggregateCommitsDaily_pendingCommit_countsTowardTheCountButNotTheAverage() {
        insertCommit("enriched", 30, 10, "COMPLETE", DAY_ONE);   // 40 changed lines
        insertCommit("pending", 0, 0, "PENDING", DAY_ONE);

        DailyCommitsProjection row = onlyDailyCommitsRow();

        assertThat(row.getCommitsCount()).isEqualTo(2);
        assertThat(row.getAvgSize()).isEqualTo(40.0);
    }

    @Test
    void aggregateCommitsDaily_allCommitsPending_countsThemButReturnsNoAverage() {
        // Nothing to average, so the projection reports null and the calculator stores 0.0 -
        // indistinguishable on the read side from a day of genuinely empty commits.
        insertCommit("pending-a", 0, 0, "PENDING", DAY_ONE);
        insertCommit("pending-b", 0, 0, "PENDING", DAY_ONE);

        DailyCommitsProjection row = onlyDailyCommitsRow();

        assertThat(row.getCommitsCount()).isEqualTo(2);
        assertThat(row.getAvgSize()).isNull();
    }

    @Test
    void aggregateCommitsDaily_skippedAndFailedCommits_areExcludedFromTheAverage() {
        insertCommit("enriched", 10, 10, "COMPLETE", DAY_ONE);   // 20 changed lines
        insertCommit("skipped", 0, 0, "SKIPPED", DAY_ONE);
        insertCommit("failed", 0, 0, "FAILED", DAY_ONE);

        DailyCommitsProjection row = onlyDailyCommitsRow();

        assertThat(row.getCommitsCount()).isEqualTo(3);
        assertThat(row.getAvgSize()).isEqualTo(20.0);
    }

    @Test
    void aggregateCommitsDaily_enrichedEmptyCommit_isAveragedAsZero() {
        // A merge with no diff is measured, not missing: it belongs in the average.
        insertCommit("enriched-empty", 0, 0, "COMPLETE", DAY_ONE);
        insertCommit("enriched-full", 0, 20, "COMPLETE", DAY_ONE);

        DailyCommitsProjection row = onlyDailyCommitsRow();

        assertThat(row.getCommitsCount()).isEqualTo(2);
        assertThat(row.getAvgSize()).isEqualTo(10.0);
    }

    @Test
    void aggregateChurnDaily_pendingCommit_doesNotDiluteTheRatio() {
        insertCommit("enriched", 30, 10, "COMPLETE", DAY_ONE);
        insertCommit("pending", 0, 0, "PENDING", DAY_ONE);

        DailyChurnProjection row = onlyChurnRow();

        assertThat(row.getAdditions()).isEqualTo(30);
        assertThat(row.getDeletions()).isEqualTo(10);
    }

    @Test
    void aggregateChurnDaily_allCommitsPending_returnsNoRowForThatDay() {
        // Filtered in the where clause rather than inside the sums, so the day disappears
        // instead of being stored as a ratio of 0.0 that would read as an all-additions day.
        insertCommit("pending", 0, 0, "PENDING", DAY_ONE);
        insertCommit("enriched-other-day", 10, 30, "COMPLETE", DAY_TWO);

        List<DailyChurnProjection> rows = churnRows();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAdditions()).isEqualTo(10);
        assertThat(rows.get(0).getDeletions()).isEqualTo(30);
    }

    @Test
    void aggregateChurnDaily_enrichedEmptyCommit_stillReturnsARowSoTheGuardStoresZero() {
        insertCommit("enriched-empty", 0, 0, "COMPLETE", DAY_ONE);

        DailyChurnProjection row = onlyChurnRow();

        assertThat(row.getAdditions()).isZero();
        assertThat(row.getDeletions()).isZero();
    }

    // -------------------------------------------------------------------------

    private DailyCommitsProjection onlyDailyCommitsRow() {
        List<DailyCommitsProjection> rows = commitRepository.aggregateCommitsDailyByRepoIdsAndIdentity(
                List.of(repoId), null, CalcUtils.emailsOrSentinel(Set.of(EMAIL)), FROM, TO);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private List<DailyChurnProjection> churnRows() {
        return commitRepository.aggregateChurnDailyByRepoIdsAndIdentity(
                List.of(repoId), null, CalcUtils.emailsOrSentinel(Set.of(EMAIL)), FROM, TO);
    }

    private DailyChurnProjection onlyChurnRow() {
        List<DailyChurnProjection> rows = churnRows();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private void insertCommit(String hashPrefix, int additions, int deletions,
                              String statsStatus, Instant when) {
        jdbc.update(
                "INSERT INTO git_commits (repository_id, hash, author_name, author_email, "
                        + "author_github_id, author_date, message, additions, deletions, stats_status) "
                        + "VALUES (?, ?, 'Fixture', ?, NULL, ?, 'msg', ?, ?, ?)",
                repoId,
                hashPrefix + "-" + System.nanoTime(),
                EMAIL,
                Timestamp.from(when),
                additions,
                deletions,
                statsStatus);
    }
}
