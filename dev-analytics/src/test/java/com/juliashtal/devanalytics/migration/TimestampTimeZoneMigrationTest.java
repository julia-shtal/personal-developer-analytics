package com.juliashtal.devanalytics.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that no column backing an {@code Instant} is left as TIMESTAMP WITHOUT TIME ZONE, whose
 * stored wall clock only has a meaning if the reader assumes the writer's zone.
 *
 * <p>Runs the chain for real against a throwaway schema, because the invariant is a property of
 * the migrations rather than of any entity mapping.</p>
 */
@SpringBootTest
class TimestampTimeZoneMigrationTest {

    /** Throwaway schema so staging migrations cannot touch the developer's real data. */
    private static final String SCHEMA = "timestamp_tz_migration_test";

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void fullChain_leavesNoTimestampWithoutTimeZoneColumn() {
        migrateToLatest();

        assertThat(timestampWithoutTimeZoneColumns()).isEmpty();
    }

    @Test
    void v65_convertsEveryColumnV55LeftBehind() {
        migrateTo("64");
        List<String> before = timestampWithoutTimeZoneColumns();

        migrateTo("65");

        assertThat(before).hasSize(22);
        assertThat(timestampWithoutTimeZoneColumns()).isEmpty();
    }

    @Test
    void v65_preservesNotNullDefaultsOnConvertedColumns() {
        migrateToLatest();

        assertThat(columnDefault("teams", "created_at")).isEqualTo("now()");
        assertThat(isNullable("teams", "created_at")).isEqualTo("NO");
    }

    @Test
    void v65_reinterpretsExistingValuesAsUtc() {
        migrateTo("64");
        Long managerId = jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".users (username, email, password_hash) "
                        + "VALUES ('tz-fixture', 'tz-fixture@example.com', 'x') RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO " + SCHEMA + ".teams (name, manager_id, created_at) "
                + "VALUES ('tz-fixture', ?, TIMESTAMP '2026-03-10 23:30:00')", managerId);

        migrateTo("65");

        String converted = jdbc.queryForObject(
                "SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') "
                        + "FROM " + SCHEMA + ".teams WHERE name = 'tz-fixture'",
                String.class);
        assertThat(converted).isEqualTo("2026-03-10 23:30:00");
    }

    // -------------------------------------------------------------------------
    // Flyway staging
    // -------------------------------------------------------------------------

    private void migrateToLatest() {
        configure().load().migrate();
    }

    /** Applies the migration chain up to and including {@code version} in the throwaway schema. */
    private void migrateTo(String version) {
        configure().target(MigrationVersion.fromVersion(version)).load().migrate();
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration configure() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
    }

    private List<String> timestampWithoutTimeZoneColumns() {
        return jdbc.queryForList(
                "SELECT table_name || '.' || column_name FROM information_schema.columns "
                        + "WHERE table_schema = ? AND data_type = 'timestamp without time zone' "
                        // Flyway owns its history table; its columns back no entity.
                        + "AND table_name <> 'flyway_schema_history' "
                        + "ORDER BY table_name, column_name",
                String.class, SCHEMA);
    }

    private String columnDefault(String table, String column) {
        return jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                String.class, SCHEMA, table, column);
    }

    private String isNullable(String table, String column) {
        return jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                String.class, SCHEMA, table, column);
    }
}
