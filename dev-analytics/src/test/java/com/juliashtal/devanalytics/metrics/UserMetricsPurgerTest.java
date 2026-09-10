package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.service.UserMetricsPurger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test for the stale-row guarantee behind an identity change.
 *
 * <p>Calculators upsert and never delete, so rows the previous identity produced must be removed
 * outright rather than recomputed over. Team-scoped rows go too, rebuilt only by the next team
 * calculation. The other user's rows are the control for a delete keyed too loosely.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserMetricsPurger.class)
class UserMetricsPurgerTest {

    @Autowired UserMetricsPurger purger;
    @Autowired JdbcTemplate jdbc;

    private static final LocalDate DAY = LocalDate.of(2026, 3, 2);

    private Long userId;
    private Long otherUserId;
    private Long teamId;

    @BeforeEach
    void seed() {
        userId = insertUser();
        otherUserId = insertUser();
        teamId = insertTeam(userId);

        insertSnapshot(userId, null, "DAILY_COMMITS_COUNT", 7);
        insertSnapshot(userId, teamId, "DAILY_COMMITS_COUNT", 7);
        insertSnapshot(otherUserId, null, "DAILY_COMMITS_COUNT", 3);

        insertCoverage(userId);
        insertCoverage(otherUserId);
    }

    @Test
    void purge_userWithPersonalAndTeamSnapshots_removesBoth() {
        purger.purge(userId);

        assertThat(snapshotCount(userId)).isZero();
    }

    @Test
    void purge_userWithCoverage_clearsTheLedgerSoTheBackfillRevisitsThoseDays() {
        purger.purge(userId);

        assertThat(coverageCount(userId)).isZero();
    }

    @Test
    void purge_otherUsersRows_areUntouched() {
        purger.purge(userId);

        assertThat(snapshotCount(otherUserId)).isEqualTo(1);
        assertThat(coverageCount(otherUserId)).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private long snapshotCount(Long uid) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM metric_snapshots WHERE user_id = ?", Long.class, uid);
        return n == null ? 0 : n;
    }

    private long coverageCount(Long uid) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM metric_coverage WHERE user_id = ?", Long.class, uid);
        return n == null ? 0 : n;
    }

    private Long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "purge-user-" + System.nanoTime(),
                "purge-" + System.nanoTime() + "@attribution-test.example",
                "fixture-hash");
    }

    private Long insertTeam(Long managerId) {
        return jdbc.queryForObject(
                "INSERT INTO teams (name, manager_id) VALUES (?, ?) RETURNING id",
                Long.class, "purge-team-" + System.nanoTime(), managerId);
    }

    private void insertSnapshot(Long uid, Long team, String metricType, double value) {
        jdbc.update(
                "INSERT INTO metric_snapshots (user_id, team_id, date, metric_type, value) "
                        + "VALUES (?, ?, ?, ?, ?)",
                uid, team, DAY, metricType, value);
    }

    private void insertCoverage(Long uid) {
        jdbc.update("INSERT INTO metric_coverage (user_id, date) VALUES (?, ?) "
                + "ON CONFLICT (user_id, date) DO NOTHING", uid, DAY);
    }
}
