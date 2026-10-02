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
 * Pins the V73 column add: a summary written before validation reporting existed must come out of
 * the migration with a null validation_report rather than a manufactured empty report.
 *
 * <p>Runs the migration for real against a throwaway schema — stage to V72, seed a summary while
 * the column still does not exist, then apply V73.</p>
 */
@SpringBootTest
class MetricSummaryValidationReportMigrationTest {

    private static final String SCHEMA = "metric_summary_validation_report_migration_test";

    private static final LocalDate FROM = LocalDate.of(2026, 3, 2);
    private static final LocalDate TO = LocalDate.of(2026, 3, 8);

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void backfill_summaryWrittenBeforeV73_staysNullRatherThanAnEmptyReport() {
        migrateTo("72");
        seedSummary();

        migrateTo("73");

        assertThat(validationReports()).containsExactly((String) null);
    }

    @Test
    void v73_appliesCleanlyOverTheFullMigrationChain() {
        migrateTo("73");

        assertThat(jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = 'metric_summaries' "
                        + "AND column_name = 'validation_report'",
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
    // Fixtures, written against the V72 schema
    // -------------------------------------------------------------------------

    private Long seedUser() {
        return jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".users (username, email, password_hash) "
                        + "VALUES ('validation-fixture', 'validation-fixture@example.com', 'x') RETURNING id",
                Long.class);
    }

    /** Inserts with runtime_version (added by V71, present by V72) but without validation_report. */
    private void seedSummary() {
        jdbc.update(
                "INSERT INTO " + SCHEMA + ".metric_summaries "
                        + "(user_id, period_from, period_to, scope, headline, model_name, prompt_version, runtime_version) "
                        + "VALUES (?, ?, ?, 'PERSONAL', 'Pre-validation summary', 'llama3.2', 'aaaaaaaaaaaaaaaa', 'UNKNOWN')",
                seedUser(), FROM, TO);
    }

    private List<String> validationReports() {
        return jdbc.queryForList(
                "SELECT validation_report FROM " + SCHEMA + ".metric_summaries ORDER BY id",
                String.class);
    }
}
