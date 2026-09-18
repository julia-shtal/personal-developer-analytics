package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the scope of commit-hash uniqueness: one revision per repository, not per table.
 *
 * <p>A fork and its upstream share revisions and both must be collectable, while within one
 * repository the hash is still the identity the collectors deduplicate on.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommitHashScopeQueryTest {

    @Autowired GitCommitEntityRepository commitRepository;
    @Autowired JdbcTemplate jdbc;

    private static final String SHARED_HASH = "c0ffee00c0ffee00c0ffee00c0ffee00c0ffee00";
    private static final Instant WHEN = Instant.parse("2026-03-02T10:00:00Z");

    private Long repoA;
    private Long repoB;

    @BeforeEach
    void seed() {
        Long userId = insertUser();
        Long dataSourceId = insertDataSource(userId);
        repoA = insertRepo(dataSourceId, "upstream-" + System.nanoTime());
        repoB = insertRepo(dataSourceId, "fork-" + System.nanoTime());
    }

    @Test
    void sameHash_inTwoRepositories_isAccepted() {
        insertCommit(repoA, SHARED_HASH);

        assertThatCode(() -> insertCommit(repoB, SHARED_HASH)).doesNotThrowAnyException();
        assertThat(commitRepository.findByRepositoryIdAndHash(repoA, SHARED_HASH)).isPresent();
        assertThat(commitRepository.findByRepositoryIdAndHash(repoB, SHARED_HASH)).isPresent();
    }

    @Test
    void sameHash_twiceInOneRepository_isRejected() {
        insertCommit(repoA, SHARED_HASH);

        assertThatThrownBy(() -> insertCommit(repoA, SHARED_HASH))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByRepositoryIdAndHash_resolvesTheRowInTheRepositoryAsked() {
        insertCommit(repoA, SHARED_HASH);
        insertCommit(repoB, SHARED_HASH);

        Long idInA = commitRepository.findByRepositoryIdAndHash(repoA, SHARED_HASH)
                .orElseThrow().getId();
        Long idInB = commitRepository.findByRepositoryIdAndHash(repoB, SHARED_HASH)
                .orElseThrow().getId();

        assertThat(idInA).isNotEqualTo(idInB);
    }

    // -------------------------------------------------------------------------

    private Long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "hash-scope-" + System.nanoTime(),
                "hash-scope-" + System.nanoTime() + "@scope-test.example",
                "fixture-hash");
    }

    private Long insertDataSource(Long userId) {
        return jdbc.queryForObject(
                "INSERT INTO data_source_configs (user_id, type, name, path) "
                        + "VALUES (?, 'GIT_LOCAL', ?, ?) RETURNING id",
                Long.class, userId, "hash-scope-ds-" + System.nanoTime(), "/tmp/hash-scope");
    }

    private Long insertRepo(Long dataSourceId, String name) {
        return jdbc.queryForObject(
                "INSERT INTO git_repositories (data_source_id, name, local_path, repo_type) "
                        + "VALUES (?, ?, ?, 'LOCAL') RETURNING id",
                Long.class, dataSourceId, name, "/tmp/" + name);
    }

    private void insertCommit(Long repoId, String hash) {
        jdbc.update(
                "INSERT INTO git_commits (repository_id, hash, author_name, author_email, "
                        + "author_date, message, additions, deletions, stats_status) "
                        + "VALUES (?, ?, 'Fixture', 'a@x.org', ?, 'msg', 1, 1, 'COMPLETE')",
                repoId, hash, Timestamp.from(WHEN));
    }
}
