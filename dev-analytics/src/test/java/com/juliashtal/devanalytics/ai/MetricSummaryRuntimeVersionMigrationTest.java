package com.juliashtal.devanalytics.ai;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the V71 backfill: a summary written before the runtime version was recorded must come
 * out of the migration carrying the UNKNOWN placeholder rather than a null.
 *
 * <p>Runs the migration for real against a throwaway schema — stage to V70, seed a summary
 * while the column still does not exist, then apply V71.</p>
 */
@SpringBootTest
class MetricSummaryRuntimeVersionMigrationTest {

    private static final String SCHEMA = "metric_summary_runtime_version_migration_test";

    private static final LocalDate FROM = LocalDate.of(2026, 3, 2);
    private static final LocalDate TO = LocalDate.of(2026, 3, 8);

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void backfill_summaryWrittenBeforeV71_becomesUnknownRatherThanNull() {
        migrateTo("70");
        seedSummary();

        migrateTo("71");

        assertThat(runtimeVersions()).containsExactly("UNKNOWN");
    }

    @Test
    void v71_appliesCleanlyOverTheFullMigrationChain() {
        // A migration that only works against an already-populated developer database is a
        // migration that will fail on a fresh install.
        migrateTo("71");

        assertThat(jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = 'metric_summaries' "
                        + "AND column_name = 'runtime_version'",
                String.class, SCHEMA)).isEqualTo("YES");
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
    // Fixtures, written against the V70 schema
    // -------------------------------------------------------------------------

    private Long seedUser() {
        return jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".users (username, email, password_hash) "
                        + "VALUES ('summary-fixture', 'summary-fixture@example.com', 'x') RETURNING id",
                Long.class);
    }

    /** Inserts without runtime_version, which is the only state V71 can observe. */
    private void seedSummary() {
        jdbc.update(
                "INSERT INTO " + SCHEMA + ".metric_summaries "
                        + "(user_id, period_from, period_to, scope, headline, model_name, prompt_version) "
                        + "VALUES (?, ?, ?, 'PERSONAL', 'Pre-runtime-version summary', 'llama3.2', 'aaaaaaaaaaaaaaaa')",
                seedUser(), FROM, TO);
    }

    private List<String> runtimeVersions() {
        return jdbc.queryForList(
                "SELECT runtime_version FROM " + SCHEMA + ".metric_summaries ORDER BY id",
                String.class);
    }
}
