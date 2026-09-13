package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.git.model.StatsSkipReason;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the V63 backfill: a row skipped before the causes were distinguished must come out of the
 * migration as UNKNOWN rather than attributed to either cause.
 *
 * <p>Runs the migration for real against a throwaway schema — stage to V62, seed a SKIPPED row
 * while the column still does not exist, then apply V63 — because a fixture created after V63 has
 * already run cannot observe which literal the migration chose.</p>
 */
@SpringBootTest
class StatsSkipReasonMigrationTest {

    /** Throwaway schema so staging migrations cannot touch the developer's real data. */
    private static final String SCHEMA = "stats_skip_reason_migration_test";

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 3, 2, 10, 0);

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void backfill_rowSkippedBeforeV63_becomesUnknownRatherThanACause() {
        migrateTo("62");
        seedSkippedCommit();

        migrateTo("63");

        assertThat(skipReasonOfSeededCommit()).isEqualTo(StatsSkipReason.UNKNOWN.name());
    }

    @Test
    void backfill_rowAlreadyCompleteBeforeV63_keepsNullReason() {
        migrateTo("62");
        seedCommit("COMPLETE");

        migrateTo("63");

        assertThat(skipReasonOf("commit-fixture")).isNull();
    }

    @Test
    void backfill_skippedPullRequestBeforeV63_becomesUnknown() {
        migrateTo("62");
        seedSkippedPullRequest();

        migrateTo("63");

        String reason = jdbc.queryForObject(
                "SELECT stats_skip_reason FROM " + SCHEMA + ".github_pull_requests WHERE number = 1",
                String.class);
        assertThat(reason).isEqualTo(StatsSkipReason.UNKNOWN.name());
    }

    @Test
    void v63_appliesCleanlyOverTheFullMigrationChain() {
        // A migration that only works against an already-populated developer database is a
        // migration that will fail on a fresh install.
        migrateTo("63");

        Integer columns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = ? AND column_name = 'stats_skip_reason' "
                        + "AND character_maximum_length = 32",
                Integer.class, SCHEMA);

        assertThat(columns).isEqualTo(2);
    }

    @Test
    void skipReasonIndexes_arePartialOnSkippedRows() {
        migrateTo("63");

        assertThat(indexPredicate("ix_git_commits_skip_reason"))
                .isEqualTo("((stats_status)::text = 'SKIPPED'::text)");
        assertThat(indexPredicate("ix_github_prs_skip_reason"))
                .isEqualTo("((stats_status)::text = 'SKIPPED'::text)");
    }

    @Test
    void skipReasonIndexes_keyRepositoryIdBeforeSkipReason() {
        migrateTo("63");

        assertThat(indexColumns("ix_git_commits_skip_reason"))
                .containsExactly("repository_id", "stats_skip_reason");
        assertThat(indexColumns("ix_github_prs_skip_reason"))
                .containsExactly("repository_id", "stats_skip_reason");
    }

    @Test
    void skipReasonColumn_acceptsEveryDeclaredEnumValue() {
        migrateTo("63");
        seedCommit("SKIPPED");

        for (StatsSkipReason reason : StatsSkipReason.values()) {
            jdbc.update("UPDATE " + SCHEMA + ".git_commits SET stats_skip_reason = ? "
                    + "WHERE hash = 'commit-fixture'", reason.name());
            assertThat(skipReasonOf("commit-fixture")).isEqualTo(reason.name());
        }
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

    private String indexPredicate(String indexName) {
        return jdbc.queryForObject(
                "SELECT pg_get_expr(i.indpred, i.indrelid) FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indexrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE c.relname = ? AND n.nspname = ?",
                String.class, indexName, SCHEMA);
    }

    /** Key columns in index order — the order is what lets the breakdown scan one repository. */
    private List<String> indexColumns(String indexName) {
        return jdbc.queryForList(
                "SELECT a.attname FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indexrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                        + "WHERE c.relname = ? AND n.nspname = ? "
                        + "ORDER BY array_position(i.indkey::smallint[], a.attnum)",
                String.class, indexName, SCHEMA);
    }

    // -------------------------------------------------------------------------
    // Fixtures, written against the V62 schema
    // -------------------------------------------------------------------------

    private Long seedRepo() {
        Long userId = jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".users (username, email, password_hash) "
                        + "VALUES ('skip-fixture', 'skip-fixture@example.com', 'x') RETURNING id",
                Long.class);
        Long dataSourceId = jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".data_source_configs (user_id, type, name, path) "
                        + "VALUES (?, 'GIT_LOCAL', 'skip-ds', '/tmp/skip-ds') RETURNING id",
                Long.class, userId);
        return jdbc.queryForObject(
                "INSERT INTO " + SCHEMA + ".git_repositories (data_source_id, name, local_path, repo_type) "
                        + "VALUES (?, 'skip-repo', '/tmp/skip-repo', 'LOCAL') RETURNING id",
                Long.class, dataSourceId);
    }

    private void seedSkippedCommit() {
        seedCommit("SKIPPED");
    }

    private void seedCommit(String statsStatus) {
        Long repoId = seedRepo();
        jdbc.update(
                "INSERT INTO " + SCHEMA + ".git_commits (repository_id, hash, author_name, "
                        + "author_email, author_date, message, additions, deletions, stats_status) "
                        + "VALUES (?, 'commit-fixture', 'Fixture', 'fixture@x.org', ?, 'msg', 5, 5, ?)",
                repoId, WHEN, statsStatus);
    }

    private void seedSkippedPullRequest() {
        Long repoId = seedRepo();
        jdbc.update(
                "INSERT INTO " + SCHEMA + ".github_pull_requests (repository_id, number, title, "
                        + "state, merged, created_at, stats_status) "
                        + "VALUES (?, 1, 'Fixture PR', 'closed', false, ?, 'SKIPPED')",
                repoId, WHEN);
    }

    private String skipReasonOfSeededCommit() {
        return skipReasonOf("commit-fixture");
    }

    private String skipReasonOf(String hash) {
        return jdbc.queryForObject(
                "SELECT stats_skip_reason FROM " + SCHEMA + ".git_commits WHERE hash = ?",
                String.class, hash);
    }
}
