package com.juliashtal.devanalytics.issue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins that an issue title is stored at the length the upstream tracker allows.
 *
 * <p>Jira caps a summary at 255 characters and GitHub does not, so a width that suits one
 * tracker silently rejects the other; the stored type has to accommodate the looser source.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IssueTitleWidthQueryTest {

    @Autowired JdbcTemplate jdbc;

    private Long dataSourceId;

    @BeforeEach
    void seed() {
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "title-width-" + System.nanoTime(),
                "title-width-" + System.nanoTime() + "@width-test.example",
                "fixture-hash");
        dataSourceId = jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, base_url) "
                        + "VALUES (?, 'GITHUB', ?, 'https://api.github.com') RETURNING id",
                Long.class, userId, "title-width-ds-" + System.nanoTime());
    }

    @Test
    void insertIssue_titleLongerThanJiraCap_isStoredWhole() {
        String title = "x".repeat(550);

        assertThatCode(() -> insertIssue("owner/repo#1509", title)).doesNotThrowAnyException();

        String stored = jdbc.queryForObject(
                "SELECT title FROM issues WHERE data_source_id = ? AND source_issue_key = ?",
                String.class, dataSourceId, "owner/repo#1509");
        assertThat(stored).hasSize(550).isEqualTo(title);
    }

    @Test
    void issuesTitle_isNotNarrowerThanItsSiblingColumns() {
        assertThat(columnLimit("issues", "title"))
                .isEqualTo(columnLimit("github_pull_requests", "title"));
    }

    // -------------------------------------------------------------------------

    private void insertIssue(String key, String title) {
        jdbc.update(
                "INSERT INTO issues (data_source_id, source_issue_key, title, source) "
                        + "VALUES (?, ?, ?, 'GITHUB')",
                dataSourceId, key, title);
    }

    /** Null for an unbounded type, which is what both columns are expected to be. */
    private Integer columnLimit(String table, String column) {
        return jdbc.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
    }
}
