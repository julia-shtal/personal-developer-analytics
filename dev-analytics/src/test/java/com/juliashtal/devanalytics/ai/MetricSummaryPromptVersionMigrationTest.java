package com.juliashtal.devanalytics.ai;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the V64 backfill: a summary written before prompts were versioned must come out of the
 * migration labelled PRE_VERSIONING rather than attributed to whichever prompt is current.
 *
 * <p>Runs the migration for real against a throwaway schema — stage to V63, seed a summary while
 * the column still does not exist, then apply V64 — because a fixture created after V64 has
 * already run cannot observe which literal the migration chose.</p>
 */
@SpringBootTest
class MetricSummaryPromptVersionMigrationTest {

    /** Throwaway schema so staging migrations cannot touch the developer's real data. */
    private static final String SCHEMA = "metric_summary_prompt_version_migration_test";

    private static final LocalDate FROM = LocalDate.of(2026, 3, 2);
    private static final LocalDate TO = LocalDate.of(2026, 3, 8);

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void backfill_summaryWrittenBeforeV64_becomesPreVersioningRatherThanACurrentPrompt() {
        migrateTo("63");
        seedSummary();

        migrateTo("64");

        assertThat(promptVersions()).containsExactly("PRE_VERSIONING");
    }

    @Test
    void v64_appliesCleanlyOverTheFullMigrationChain() {
        // A migration that only works against an already-populated developer database is a
        // migration that will fail on a fresh install.
        migrateTo("64");

        assertThat(jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = 'metric_summaries' "
                        + "AND column_name = 'prompt_version'",
                String.class, SCHEMA)).isEqualTo("NO");
    }

    @Test
    void identityIndex_keysPromptVersionLast() {
        migrateTo("64");

        assertThat(indexExpressions("uix_metric_summaries_identity")).endsWith("prompt_version)");
    }

    @Test
    void identityIndex_twoPromptVersionsForOneScopeAndPeriod_coexist() {
        migrateTo("64");
        Long userId = seedUser();

        insertSummary(userId, "aaaaaaaaaaaaaaaa");
        insertSummary(userId, "bbbbbbbbbbbbbbbb");

        assertThat(promptVersions()).containsExactly("aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb");
    }

    @Test
    void identityIndex_sameScopePeriodAndPromptVersionTwice_isRejected() {
        migrateTo("64");
        Long userId = seedUser();

        insertSummary(userId, "aaaaaaaaaaaaaaaa");

        assertThatThrownBy(() -> insertSummary(userId, "aaaaaaaaaaaaaaaa"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // -------------------------------------------------------------------------
    // Flyway staging
    // -------------------------------------------------------------------------

    /** Applies the migration chain up to and including {@code version} in the throwaway schema. */
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

    private String indexExpressions(String indexName) {
        return jdbc.queryForObject(
                "SELECT pg_get_indexdef(c.oid) FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE c.relname = ? AND n.nspname = ?",
                String.class, indexName, SCHEMA);
    }

    // -------------------------------------------------------------------------
    // Fixtures, written against the V63 schema
    // -------------------------------------------------------------------------

    private Long seedUser() {
        return jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".users (username, email, password_hash) "
                        + "VALUES ('summary-fixture', 'summary-fixture@example.com', 'x') RETURNING id",
                Long.class);
    }

    /** Inserts without prompt_version, which is the only state V64 can observe. */
    private void seedSummary() {
        jdbc.update(
                "INSERT INTO " + SCHEMA + ".metric_summaries "
                        + "(user_id, period_from, period_to, scope, headline, model_name) "
                        + "VALUES (?, ?, ?, 'PERSONAL', 'Pre-versioning summary', 'llama3.2')",
                seedUser(), FROM, TO);
    }

    private void insertSummary(Long userId, String promptVersion) {
        jdbc.update(
                "INSERT INTO " + SCHEMA + ".metric_summaries "
                        + "(user_id, period_from, period_to, scope, headline, model_name, prompt_version) "
                        + "VALUES (?, ?, ?, 'PERSONAL', 'Summary', 'llama3.2', ?)",
                userId, FROM, TO, promptVersion);
    }

    private List<String> promptVersions() {
        return jdbc.queryForList(
                "SELECT prompt_version FROM " + SCHEMA + ".metric_summaries ORDER BY prompt_version",
                String.class);
    }
}
