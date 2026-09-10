package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterises the two queries {@code RepoService} uses to decide whether a caller may scope
 * by a repository, against the real schema: {@code existsAccessibleRepo} (can this user reach
 * it?) and {@code existsByIdAndTeamId} (does this team own it?).
 *
 * <p>Run against the actual database rather than mocked because the first is native and exists
 * solely to inherit the {@code user_accessible_repos} view's four branches — a mock would
 * assert only that the method was called. One test per branch, since the branch the check
 * exists to cover (TEAM) is the one a hand-rolled owned-or-subscribed test omits.
 *
 * <p>The two queries answer deliberately different questions, so the pair of tests at the end
 * pins the case that separates them: a repo reachable by a user but outside the team.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepoEntitlementQueryTest {

    @Autowired GitRepositoryEntityRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void existsAccessibleRepo_userOwnsTheDataSource_returnsTrue() {
        Long userId = insertUser();
        Long repoId = insertRepo(insertDataSource(userId, null));

        assertThat(repository.existsAccessibleRepo(userId, repoId)).isTrue();
    }

    @Test
    void existsAccessibleRepo_userSubscribedToAnotherUsersRepo_returnsTrue() {
        Long ownerId = insertUser();
        Long subscriberId = insertUser();
        Long repoId = insertRepo(insertDataSource(ownerId, null));
        insertRegistration(subscriberId, repoId);

        assertThat(repository.existsAccessibleRepo(subscriberId, repoId)).isTrue();
    }

    /** The branch the old check missed: team access without an individual subscription. */
    @Test
    void existsAccessibleRepo_teamMemberNeverSubscribed_returnsTrue() {
        Long managerId = insertUser();
        Long memberId = insertUser();
        Long teamId = insertTeam(managerId);
        insertTeamMember(teamId, memberId);
        Long repoId = insertRepo(insertDataSource(managerId, teamId));

        assertThat(repository.existsAccessibleRepo(memberId, repoId)).isTrue();
    }

    /** Managers are on teams.manager_id, not team_members, so they need their own branch. */
    @Test
    void existsAccessibleRepo_teamManagerNotInMembersTable_returnsTrue() {
        Long managerId = insertUser();
        Long dataSourceOwnerId = insertUser();
        Long teamId = insertTeam(managerId);
        Long repoId = insertRepo(insertDataSource(dataSourceOwnerId, teamId));

        assertThat(repository.existsAccessibleRepo(managerId, repoId)).isTrue();
    }

    @Test
    void existsAccessibleRepo_strangerWithNoPath_returnsFalse() {
        Long ownerId = insertUser();
        Long strangerId = insertUser();
        Long repoId = insertRepo(insertDataSource(ownerId, null));

        assertThat(repository.existsAccessibleRepo(strangerId, repoId)).isFalse();
    }

    @Test
    void existsAccessibleRepo_repoIdDoesNotExist_returnsFalse() {
        Long userId = insertUser();

        assertThat(repository.existsAccessibleRepo(userId, -1L)).isFalse();
    }

    // ── existsByIdAndTeamId — team ownership, a different question ───────────

    @Test
    void existsByIdAndTeamId_repoBelongsToTeamDataSource_returnsTrue() {
        Long managerId = insertUser();
        Long teamId = insertTeam(managerId);
        Long repoId = insertRepo(insertDataSource(managerId, teamId));

        assertThat(repository.existsByIdAndTeamId(repoId, teamId)).isTrue();
    }

    /**
     * The divergence that makes the two queries distinct: the manager reaches this repo, and
     * the team does not own it. Personal entitlement would admit it to a team series.
     */
    @Test
    void existsByIdAndTeamId_managersOwnRepoOutsideTeam_returnsFalseWhileAccessibleIsTrue() {
        Long managerId = insertUser();
        Long teamId = insertTeam(managerId);
        Long personalRepoId = insertRepo(insertDataSource(managerId, null));

        assertThat(repository.existsAccessibleRepo(managerId, personalRepoId)).isTrue();
        assertThat(repository.existsByIdAndTeamId(personalRepoId, teamId)).isFalse();
    }

    @Test
    void existsByIdAndTeamId_repoIdDoesNotExist_returnsFalse() {
        Long teamId = insertTeam(insertUser());

        assertThat(repository.existsByIdAndTeamId(-1L, teamId)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Fixtures, inserted with JdbcTemplate so each test names the exact rows the
    // view joins on. Mirrors EarliestActivityQueryTest's approach and its notes on
    // the NOT NULL columns git_repositories and data_source_configs carry.
    // -------------------------------------------------------------------------

    private Long insertUser() {
        long n = System.nanoTime();
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "accessible-fixture-" + n,
                "accessible-fixture-" + n + "@accessible-repo-view-test.example",
                "fixture-hash");
    }

    private Long insertTeam(Long managerId) {
        return jdbc.queryForObject(
                "INSERT INTO teams (name, manager_id) VALUES (?, ?) RETURNING id",
                Long.class, "accessible-fixture-team-" + System.nanoTime(), managerId);
    }

    private void insertTeamMember(Long teamId, Long userId) {
        jdbc.update("INSERT INTO team_members (team_id, user_id) VALUES (?, ?)", teamId, userId);
    }

    private Long insertDataSource(Long userId, Long teamId) {
        return jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, team_id, type, name, path) "
                        + "VALUES (?, ?, 'GIT_LOCAL', ?, ?) RETURNING id",
                Long.class, userId, teamId,
                "accessible-fixture-ds-" + System.nanoTime(), "/tmp/accessible-fixture-ds");
    }

    private Long insertRepo(Long dataSourceId) {
        String name = "accessible-fixture-repo-" + System.nanoTime();
        return jdbc.queryForObject(
                "INSERT INTO git_repositories (data_source_id, name, local_path, repo_type) "
                        + "VALUES (?, ?, ?, 'LOCAL') RETURNING id",
                Long.class, dataSourceId, name, "/tmp/" + name);
    }

    private void insertRegistration(Long userId, Long repoId) {
        // repository_id, not repo_id: V42 renamed the column when it normalised FK naming.
        jdbc.update("INSERT INTO user_repo_registrations (user_id, repository_id) VALUES (?, ?)",
                userId, repoId);
    }
}
