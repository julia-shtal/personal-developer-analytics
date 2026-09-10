package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.model.DailyCountProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for issue attribution, including the team N× regression.
 *
 * <p>Without a per-author filter a repo-wide issue counts for every subscriber, and
 * {@code buildTeamDailySeries} then sums the members into an N× team total — the regression the
 * last test pins. The queries are native, join GitHub and Jira through one {@code COALESCE}, and
 * CAST nullable parameters, so they are exercised against the real schema. Created counts follow
 * the creator; closed counts and lead time follow the assignee.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IssueAttributionQueryTest {

    @Autowired IssueRepository issueRepository;
    @Autowired JdbcTemplate jdbc;

    private static final Instant CREATED = Instant.parse("2026-03-02T09:00:00Z");
    private static final Instant CLOSED  = Instant.parse("2026-03-02T17:00:00Z");
    private static final Instant FROM    = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO      = Instant.parse("2026-03-03T00:00:00Z");

    private static final long   A_GITHUB_ID = 101L;
    private static final long   B_GITHUB_ID = 202L;
    private static final String A_JIRA      = "jira-account-a";
    private static final String B_JIRA      = "jira-account-b";

    private Long repoId;

    @BeforeEach
    void seed() {
        Long userId = insertUser();
        Long dataSourceId = insertDataSource(userId);
        repoId = insertRepo(dataSourceId, "issue-attr-" + System.nanoTime());

        // GitHub: created by A, assigned to B. One issue, two different people on it, so a query
        // that confused creator with assignee would still find "an" issue and look correct.
        insertGithubIssue("GH-1", A_GITHUB_ID, B_GITHUB_ID);

        // Jira, reachable only through jira_project_repo_mappings: reported by A, assigned to B.
        Long jiraProjectId = insertJiraProject(insertJiraDataSource());
        insertJiraProjectRepoMapping(jiraProjectId, repoId);
        insertJiraIssue(jiraProjectId, "JIRA-1", A_JIRA, B_JIRA);
    }

    @Test
    void aggregateIssuesCreatedDailyByRepoIdsAndIdentity_creatorIdentity_countsBothSources() {
        // A reported one GitHub issue and one Jira issue.
        assertThat(created(A_GITHUB_ID, A_JIRA)).isEqualTo(2);
    }

    @Test
    void aggregateIssuesCreatedDailyByRepoIdsAndIdentity_assigneeIdentity_countsNothingCreated() {
        // B is assigned both issues but reported neither.
        assertThat(created(B_GITHUB_ID, B_JIRA)).isZero();
    }

    @Test
    void aggregateIssuesClosedDailyByRepoIdsAndIdentity_assigneeIdentity_countsBothSources() {
        assertThat(closed(B_GITHUB_ID, B_JIRA)).isEqualTo(2);
    }

    @Test
    void aggregateIssuesClosedDailyByRepoIdsAndIdentity_creatorIdentity_countsNothingClosed() {
        assertThat(closed(A_GITHUB_ID, A_JIRA)).isZero();
    }

    @Test
    void aggregateIssuesCreatedDailyByRepoIdsAndIdentity_githubOnlyIdentity_ignoresJiraIssues() {
        // The predicate is per source, so a user linked only to GitHub must not pick up Jira rows.
        assertThat(created(A_GITHUB_ID, null)).isEqualTo(1);
    }

    @Test
    void aggregateIssuesCreatedDailyByRepoIdsAndIdentity_jiraOnlyIdentity_ignoresGithubIssues() {
        assertThat(created(null, A_JIRA)).isEqualTo(1);
    }

    @Test
    void aggregateIssuesCreatedDailyByRepoIdsAndIdentity_subscriberWithNoIdentity_getsNoRows() {
        // Both parameters null must bind as typed NULLs and match nothing, not everything.
        assertThat(created(null, null)).isZero();
        assertThat(closed(null, null)).isZero();
    }

    @Test
    void aggregateIssuesClosedDailyByRepoIdsAndIdentity_summedOverTeamMembers_equalsDistinctIssues() {
        // The N× regression as the dashboard computes it: summing each member's series must
        // equal the distinct issues the team closed, not N times them.
        long teamAggregate = closed(A_GITHUB_ID, A_JIRA)
                + closed(B_GITHUB_ID, B_JIRA)
                + closed(303L, "jira-account-c");

        assertThat(teamAggregate).isEqualTo(distinctClosedIssuesInRepo());
    }

    private long created(Long githubUserId, String jiraAccountId) {
        return sum(issueRepository.aggregateIssuesCreatedDailyByRepoIdsAndIdentity(
                List.of(repoId), githubUserId, jiraAccountId, FROM, TO));
    }

    private long closed(Long githubUserId, String jiraAccountId) {
        return sum(issueRepository.aggregateIssuesClosedDailyByRepoIdsAndIdentity(
                List.of(repoId), githubUserId, jiraAccountId, FROM, TO));
    }

    private static long sum(List<DailyCountProjection> rows) {
        return rows.stream().mapToLong(DailyCountProjection::getCount).sum();
    }

    /** Ground truth for the team assertion, counted straight from the table. */
    private long distinctClosedIssuesInRepo() {
        Long count = jdbc.queryForObject(
                "SELECT count(DISTINCT i.id) FROM issues i "
                        + "LEFT JOIN jira_project_repo_mappings rm ON rm.jira_project_id = i.jira_project_id "
                        + "WHERE COALESCE(i.repository_id, rm.repository_id) = ? "
                        + "AND i.closed_at IS NOT NULL",
                Long.class, repoId);
        return count == null ? 0 : count;
    }

    // -------------------------------------------------------------------------
    // Fixtures — see EarliestActivityQueryTest for the schema constraints these satisfy.
    // -------------------------------------------------------------------------

    private Long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "issue-attr-user-" + System.nanoTime(),
                "issue-attr-" + System.nanoTime() + "@attribution-test.example",
                "fixture-hash");
    }

    private Long insertDataSource(Long userId) {
        return jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, path) "
                        + "VALUES (?, 'GIT_LOCAL', ?, ?) RETURNING id",
                Long.class, userId, "issue-attr-ds-" + System.nanoTime(), "/tmp/issue-attr-ds");
    }

    private Long insertRepo(Long dataSourceId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO git_repositories (data_source_id, name, local_path, repo_type) "
                        + "VALUES (?, ?, ?, 'LOCAL') RETURNING id",
                Long.class, dataSourceId, name, "/tmp/" + name);
    }

    private void insertGithubIssue(String key, Long creatorGithubId, Long assigneeGithubId) {
        jdbc.update(
                "INSERT INTO issues (repository_id, source_issue_key, title, state, created_at, "
                        + "closed_at, creator_github_id, assignee_github_id, source) "
                        + "VALUES (?, ?, 'Issue', 'closed', ?, ?, ?, ?, 'GITHUB')",
                repoId, key + "-" + System.nanoTime(),
                Timestamp.from(CREATED), Timestamp.from(CLOSED), creatorGithubId, assigneeGithubId);
    }

    private Long insertJiraDataSource() {
        return jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, base_url) "
                        + "VALUES (?, 'JIRA', ?, ?) RETURNING id",
                Long.class, insertUser(), "issue-attr-jira-ds-" + System.nanoTime(),
                "https://issue-attr-" + System.nanoTime() + ".atlassian.net");
    }

    private Long insertJiraProject(Long dataSourceId) {
        return jdbc.queryForObject(
                "INSERT INTO jira_projects (data_source_id, project_key, base_url_normalized) "
                        + "VALUES (?, ?, ?) RETURNING id",
                Long.class, dataSourceId, "ISS" + System.nanoTime(), "issue-attr-" + System.nanoTime());
    }

    private void insertJiraProjectRepoMapping(Long jiraProjectId, Long repositoryId) {
        jdbc.update(
                "INSERT INTO jira_project_repo_mappings (jira_project_id, repository_id) VALUES (?, ?)",
                jiraProjectId, repositoryId);
    }

    private void insertJiraIssue(Long jiraProjectId, String key, String reporterAccountId, String assigneeAccountId) {
        jdbc.update(
                "INSERT INTO issues (jira_project_id, source_issue_key, title, state, created_at, "
                        + "closed_at, reporter_account_id, assignee_account_id, source) "
                        + "VALUES (?, ?, 'Issue', 'closed', ?, ?, ?, ?, 'JIRA')",
                jiraProjectId, key + "-" + System.nanoTime(),
                Timestamp.from(CREATED), Timestamp.from(CLOSED), reporterAccountId, assigneeAccountId);
    }
}
