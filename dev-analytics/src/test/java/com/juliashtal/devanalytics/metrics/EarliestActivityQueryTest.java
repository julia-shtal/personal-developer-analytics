package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterises the earliest-activity queries the backfill uses to decide how far back its
 * target range reaches. Run against the real schema because two of the three are native and
 * the issue query routes both GitHub and Jira issues through a COALESCE join.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EarliestActivityQueryTest {

    @Autowired GitCommitEntityRepository commitRepository;
    @Autowired GitHubPullRequestRepository pullRequestRepository;
    @Autowired IssueRepository issueRepository;
    @Autowired JdbcTemplate jdbc;

    private static final Instant OLDEST = Instant.parse("2026-01-05T09:00:00Z");
    private static final Instant NEWER  = Instant.parse("2026-02-05T09:00:00Z");

    private Long repoId;
    private Long otherRepoId;

    @BeforeEach
    void seed() {
        Long userId = insertUser();
        Long dataSourceId = insertDataSource(userId);

        repoId = insertRepo(dataSourceId, "earliest-fixture");
        otherRepoId = insertRepo(dataSourceId, "earliest-fixture-other");

        insertCommit(repoId, "hash-oldest", OLDEST);
        insertCommit(repoId, "hash-newer", NEWER);
        insertCommit(otherRepoId, "hash-other", Instant.parse("2025-01-01T00:00:00Z"));

        insertPr(repoId, 1, NEWER);
        insertPr(repoId, 2, OLDEST);

        insertIssue(repoId, "ISSUE-1", NEWER);
        insertIssue(repoId, "ISSUE-2", OLDEST);
    }

    @Test
    void findEarliestAuthorDate_scopedToRepoIds_returnsOldestCommitInScope() {
        assertThat(commitRepository.findEarliestAuthorDate(List.of(repoId)))
                .contains(OLDEST);
    }

    @Test
    void findEarliestAuthorDate_repoWithoutCommits_returnsEmpty() {
        Long userId = insertUser();
        Long dataSourceId = insertDataSource(userId);
        Long emptyRepoId = insertRepo(dataSourceId, "earliest-fixture-empty");

        assertThat(commitRepository.findEarliestAuthorDate(List.of(emptyRepoId)))
                .isEmpty();
    }

    @Test
    void findEarliestCreatedAt_pullRequests_returnsOldestInScope() {
        assertThat(pullRequestRepository.findEarliestCreatedAt(List.of(repoId)))
                .contains(OLDEST);
    }

    @Test
    void findEarliestCreatedAt_pullRequests_repoWithoutPrs_returnsEmpty() {
        Long userId = insertUser();
        Long dataSourceId = insertDataSource(userId);
        Long emptyRepoId = insertRepo(dataSourceId, "earliest-fixture-empty-pr");

        assertThat(pullRequestRepository.findEarliestCreatedAt(List.of(emptyRepoId)))
                .isEmpty();
    }

    @Test
    void findEarliestCreatedAt_issues_returnsOldestInScope() {
        assertThat(issueRepository.findEarliestCreatedAt(List.of(repoId)))
                .contains(OLDEST);
    }

    @Test
    void findEarliestCreatedAt_issues_repoWithoutIssues_returnsEmpty() {
        Long userId = insertUser();
        Long dataSourceId = insertDataSource(userId);
        Long emptyRepoId = insertRepo(dataSourceId, "earliest-fixture-empty-issue");

        assertThat(issueRepository.findEarliestCreatedAt(List.of(emptyRepoId)))
                .isEmpty();
    }

    @Test
    void findEarliestCreatedAt_issues_jiraRoutedIssueOlderThanGithub_participatesInMin() {
        // A Jira issue reachable only via the mapping table, older than any GitHub-routed one,
        // so the assertion fails if COALESCE drops the Jira branch from the repo-scoped MIN.
        Instant jiraOldest = Instant.parse("2025-06-01T00:00:00Z");

        Long jiraDataSourceId = insertJiraDataSource();
        Long jiraProjectId = insertJiraProject(jiraDataSourceId);
        insertJiraProjectRepoMapping(jiraProjectId, repoId);
        insertJiraIssue(jiraProjectId, "JIRA-1", jiraOldest);

        assertThat(issueRepository.findEarliestCreatedAt(List.of(repoId)))
                .contains(jiraOldest);
    }

    // -------------------------------------------------------------------------
    // Fixtures. Inserted with JdbcTemplate rather than entities so the test states the exact
    // column values the queries read, and stays readable when an unrelated field is added.
    //
    // git_commits.author_date and github_pull_requests.created_at are TIMESTAMP WITHOUT TIME
    // ZONE, so Instant fixture values are bound as UTC LocalDateTime -- see insertCommit.
    // -------------------------------------------------------------------------

    private Long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "fixture-user-" + System.nanoTime(),
                "fixture-" + System.nanoTime() + "@earliest-activity-test.example",
                "fixture-hash");
    }

    private Long insertDataSource(Long userId) {
        return jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, path) VALUES (?, 'GIT_LOCAL', ?, ?) RETURNING id",
                Long.class, userId, "fixture-ds-" + System.nanoTime(), "/tmp/fixture-ds");
    }

    private Long insertRepo(Long dataSourceId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO git_repositories (data_source_id, name, local_path, repo_type) " +
                        "VALUES (?, ?, ?, 'LOCAL') RETURNING id",
                Long.class, dataSourceId, name, "/tmp/" + name);
    }

    private void insertCommit(Long repositoryId, String hash, Instant authorDate) {
        // author_date is TIMESTAMP WITHOUT TIME ZONE. Timestamp.from(Instant) writes through the
        // JVM zone while Hibernate reads back as UTC, shifting the value; bind UTC directly.
        jdbc.update(
                "INSERT INTO git_commits (repository_id, hash, author_name, author_email, " +
                        "author_date, message, additions, deletions, stats_status) " +
                        "VALUES (?, ?, 'Fixture', 'fixture@example.com', ?, 'msg', 0, 0, 'COMPLETE')",
                repositoryId, hash, java.time.LocalDateTime.ofInstant(authorDate, java.time.ZoneOffset.UTC));
    }

    private void insertPr(Long repositoryId, int number, Instant createdAt) {
        // github_pull_requests.created_at is likewise TIMESTAMP WITHOUT TIME ZONE; see
        // insertCommit for why the UTC LocalDateTime is bound directly.
        jdbc.update(
                "INSERT INTO github_pull_requests (repository_id, number, title, author_login, " +
                        "state, merged, created_at) " +
                        "VALUES (?, ?, 'PR', 'fixture', 'closed', false, ?)",
                repositoryId, number, java.time.LocalDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC));
    }

    private void insertIssue(Long repositoryId, String key, Instant createdAt) {
        jdbc.update(
                "INSERT INTO issues (repository_id, source_issue_key, title, state, created_at, source) " +
                        "VALUES (?, ?, 'Issue', 'open', ?, 'GITHUB')",
                repositoryId, key, java.sql.Timestamp.from(createdAt));
    }

    // -------------------------------------------------------------------------
    // Jira-routing fixtures. Jira issues reach a repo only via jira_project_repo_mappings,
    // the branch IssueRepository.findEarliestCreatedAt's COALESCE exists to cover.
    // -------------------------------------------------------------------------

    private Long insertJiraDataSource() {
        // V35's chk_remote_baseurl requires base_url (not path) for any non-GIT_LOCAL type.
        return jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, base_url) VALUES (?, 'JIRA', ?, ?) RETURNING id",
                Long.class, insertUser(), "fixture-jira-ds-" + System.nanoTime(),
                "https://fixture-" + System.nanoTime() + ".atlassian.net");
    }

    private Long insertJiraProject(Long dataSourceId) {
        // base_url_normalized has been NOT NULL with no default since V39.
        return jdbc.queryForObject(
                "INSERT INTO jira_projects (data_source_id, project_key, base_url_normalized) " +
                        "VALUES (?, ?, ?) RETURNING id",
                Long.class, dataSourceId, "FIX" + System.nanoTime(), "fixture-" + System.nanoTime());
    }

    private void insertJiraProjectRepoMapping(Long jiraProjectId, Long repositoryId) {
        jdbc.update(
                "INSERT INTO jira_project_repo_mappings (jira_project_id, repository_id) VALUES (?, ?)",
                jiraProjectId, repositoryId);
    }

    private void insertJiraIssue(Long jiraProjectId, String key, Instant createdAt) {
        jdbc.update(
                "INSERT INTO issues (jira_project_id, source_issue_key, title, state, created_at, source) " +
                        "VALUES (?, ?, 'Issue', 'open', ?, 'JIRA')",
                jiraProjectId, key, java.sql.Timestamp.from(createdAt));
    }
}
