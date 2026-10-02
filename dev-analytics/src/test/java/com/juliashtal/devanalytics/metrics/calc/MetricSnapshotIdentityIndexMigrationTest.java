package com.juliashtal.devanalytics.metrics.calc;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the V72 dedup-then-index migration: of two rows sharing an identity, the one with the
 * later {@code calculated_at} survives, and the index it builds rejects a further duplicate.
 *
 * <p>Runs the migration for real against a throwaway schema — stage to V71, seed duplicate
 * rows while the index does not exist yet, then apply V72 — because a fixture created after
 * V72 has already run cannot observe which row the dedup step chose.</p>
 */
@SpringBootTest
class MetricSnapshotIdentityIndexMigrationTest {

    private static final String SCHEMA = "metric_snapshot_identity_index_migration_test";
    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void dedup_duplicateIdentity_keepsTheLaterCalculatedRow() {
        migrateTo("71");
        Long userId = seedUser();
        insertSnapshot(userId, 1.0, Instant.parse("2026-03-01T00:00:00Z"));
        insertSnapshot(userId, 2.0, Instant.parse("2026-03-02T00:00:00Z"));

        migrateTo("72");

        assertThat(values(userId)).containsExactly(2.0);
    }

    @Test
    void v72_appliesCleanlyOverTheFullMigrationChain() {
        migrateTo("72");

        assertThat(jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = 'uix_metric_snapshots_identity'",
                String.class, SCHEMA)).contains("UNIQUE");
    }

    @Test
    void identityIndex_sameIdentityAfterMigration_isRejected() {
        migrateTo("72");
        Long userId = seedUser();
        insertSnapshot(userId, 1.0, Instant.now());

        assertThatThrownBy(() -> insertSnapshot(userId, 2.0, Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // -------------------------------------------------------------------------
    // Flyway staging
    // -------------------------------------------------------------------------

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    // -------------------------------------------------------------------------
    // Fixtures, written against the V71 schema
    // -------------------------------------------------------------------------

    private Long seedUser() {
        return jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".users (username, email, password_hash) "
                        + "VALUES (?, ?, 'x') RETURNING id",
                Long.class,
                "snapshot-fixture-" + System.nanoTime(),
                "snapshot-fixture-" + System.nanoTime() + "@example.com");
    }

    /** Both calls share every identity column; only calculated_at differs. */
    private void insertSnapshot(Long userId, double value, Instant calculatedAt) {
        jdbc.update(
                "INSERT INTO " + SCHEMA + ".metric_snapshots "
                        + "(user_id, date, metric_type, value, calculated_at) "
                        + "VALUES (?, ?, 'DAILY_COMMITS_COUNT', ?, ?)",
                userId, DATE, value, Timestamp.from(calculatedAt));
    }

    private List<Double> values(Long userId) {
        return jdbc.queryForList(
                "SELECT value FROM " + SCHEMA + ".metric_snapshots WHERE user_id = ? ORDER BY value",
                Double.class, userId);
    }
}
