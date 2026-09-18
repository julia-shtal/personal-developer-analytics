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
 * Pins what a recalculation clears: one user, one scope, one window, and nothing else.
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
}
