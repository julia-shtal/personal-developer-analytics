package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins two deletion queries against {@code metric_snapshots}: what one user's recalculation
 * clears (scoped to that user, team, and window), and what the nightly retention sweep removes
 * (global, by horizon, across every user).
 *
 * <p>Run against the real schema because the scoping turns on SQL's treatment of a null
 * {@code team_id}.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MetricSnapshotPruneQueryTest {

    @Autowired MetricSnapshotRepository repository;
    @Autowired JdbcTemplate jdbc;

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 3, 31);

    private Long userId;
    private Long otherUserId;
    private Long teamId;

    @BeforeEach
    void seed() {
        userId = insertUser();
        otherUserId = insertUser();
        teamId = insertTeam(userId);
    }

    @Test
    void deleteForRecalculation_personalScope_removesOnlyThatUsersPersonalRowsInWindow() {
        insertSnapshot(userId, null, LocalDate.of(2026, 3, 10));      // in scope
        insertSnapshot(userId, null, LocalDate.of(2026, 3, 31));      // inclusive upper bound
        insertSnapshot(userId, null, LocalDate.of(2026, 3, 1));       // inclusive lower bound
        insertSnapshot(userId, null, LocalDate.of(2026, 2, 28));      // before the window
        insertSnapshot(userId, null, LocalDate.of(2026, 4, 1));       // after the window
        insertSnapshot(userId, teamId, LocalDate.of(2026, 3, 10));    // team scope
        insertSnapshot(otherUserId, null, LocalDate.of(2026, 3, 10)); // another user

        int removed = repository.deleteForRecalculation(userId, null, FROM, TO);

        assertThat(removed).isEqualTo(3);
        assertThat(countFor(userId, "team_id IS NULL")).isEqualTo(2);
        assertThat(countFor(userId, "team_id IS NOT NULL")).isEqualTo(1);
        assertThat(countFor(otherUserId, "1=1")).isEqualTo(1);
    }

    @Test
    void deleteForRecalculation_teamScope_leavesThePersonalRowsAlone() {
        insertSnapshot(userId, teamId, LocalDate.of(2026, 3, 10));
        insertSnapshot(userId, null, LocalDate.of(2026, 3, 10));

        int removed = repository.deleteForRecalculation(userId, teamId, FROM, TO);

        assertThat(removed).isEqualTo(1);
        assertThat(countFor(userId, "team_id IS NULL")).isEqualTo(1);
        assertThat(countFor(userId, "team_id IS NOT NULL")).isZero();
    }

    @Test
    void deleteForRecalculation_noRowsInWindow_removesNothingAndReportsZero() {
        insertSnapshot(userId, null, LocalDate.of(2026, 1, 15));

        assertThat(repository.deleteForRecalculation(userId, null, FROM, TO)).isZero();
        assertThat(countFor(userId, "1=1")).isEqualTo(1);
    }

    @Test
    void deleteExpired_dailyRowBeforeHorizon_isRemoved() {
        insertSnapshot(userId, null, LocalDate.of(2024, 1, 1));

        repository.deleteExpired(LocalDate.of(2024, 6, 1));

        assertThat(countFor(userId, "1=1")).isZero();
    }

    @Test
    void deleteExpired_dailyRowOnOrAfterHorizon_isKept() {
        insertSnapshot(userId, null, LocalDate.of(2024, 6, 1));

        repository.deleteExpired(LocalDate.of(2024, 6, 1));

        assertThat(countFor(userId, "1=1")).isEqualTo(1);
    }

    @Test
    void deleteExpired_aggregateRowPeriodToBeforeHorizon_isRemoved() {
        insertAggregateSnapshot(userId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));

        repository.deleteExpired(LocalDate.of(2024, 6, 1));

        assertThat(countFor(userId, "1=1")).isZero();
    }

    @Test
    void deleteExpired_aggregateRowPeriodToOnOrAfterHorizon_isKept() {
        insertAggregateSnapshot(userId, LocalDate.of(2024, 5, 26), LocalDate.of(2024, 6, 1));

        repository.deleteExpired(LocalDate.of(2024, 6, 1));

        assertThat(countFor(userId, "1=1")).isEqualTo(1);
    }

    @Test
    void deleteExpired_mixOfExpiredAndCurrent_removesOnlyExpired() {
        insertSnapshot(userId, null, LocalDate.of(2024, 1, 1));       // expired
        insertSnapshot(userId, null, LocalDate.of(2024, 6, 1));       // current
        insertAggregateSnapshot(userId, LocalDate.of(2023, 12, 1), LocalDate.of(2023, 12, 7)); // expired
        insertAggregateSnapshot(userId, LocalDate.of(2024, 5, 26), LocalDate.of(2024, 6, 1));   // current
        insertSnapshot(otherUserId, null, LocalDate.of(2024, 1, 1)); // expired, another user

        int removed = repository.deleteExpired(LocalDate.of(2024, 6, 1));

        // Global, unscoped delete against the shared dev DB (other rows may match too) — >=
        // is the only safe bound here; countFor below is what actually pins this fixture.
        assertThat(removed).isGreaterThanOrEqualTo(3);
        assertThat(countFor(userId, "1=1")).isEqualTo(2);
        assertThat(countFor(otherUserId, "1=1")).isZero();
    }

    // -------------------------------------------------------------------------

    private long countFor(Long user, String extra) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM metric_snapshots WHERE user_id = ? AND " + extra,
                Long.class, user);
        return count == null ? 0 : count;
    }

    private Long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "prune-user-" + System.nanoTime(),
                "prune-" + System.nanoTime() + "@prune-test.example",
                "fixture-hash");
    }

    private Long insertTeam(Long managerId) {
        return jdbc.queryForObject(
                "INSERT INTO teams (name, manager_id) VALUES (?, ?) RETURNING id",
                Long.class, "prune-team-" + System.nanoTime(), managerId);
    }

    private void insertSnapshot(Long user, Long team, LocalDate date) {
        jdbc.update(
                "INSERT INTO metric_snapshots (user_id, team_id, date, metric_type, value) "
                        + "VALUES (?, ?, ?, 'DAILY_COMMITS_COUNT', 1)",
                user, team, date);
    }

    private void insertAggregateSnapshot(Long user, LocalDate periodFrom, LocalDate periodTo) {
        jdbc.update(
                "INSERT INTO metric_snapshots (user_id, team_id, date, metric_type, value, period_from, period_to) "
                        + "VALUES (?, NULL, ?, 'PR_LEAD_TIME_HOURS_MEDIAN', 1, ?, ?)",
                user, periodFrom, periodFrom, periodTo);
    }
}
