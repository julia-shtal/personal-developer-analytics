package com.juliashtal.devanalytics.metrics.repository;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a point-in-time metric is read as "every row on its most recent calculation date",
 * never as a window: rows from an older calculation must not be mixed into the current figure.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MetricSnapshotLatestQueryTest {

    private static final LocalDate OLD    = LocalDate.of(2026, 3, 1);
    private static final LocalDate LATEST = LocalDate.of(2026, 3, 8);

    @Autowired MetricSnapshotRepository repository;
    @Autowired EntityManager            entityManager;

    private User                user;
    private GitRepositoryEntity repoA;
    private GitRepositoryEntity repoB;

    @BeforeEach
    void seed() {
        user = new User();
        user.setEmail("wip@example.com");
        user.setUsername("wip-user");
        user.setPasswordHash("hash");
        user.setRole(Role.DEVELOPER);
        user.setTimezone("UTC");
        entityManager.persist(user);

        DataSourceConfig ds = new DataSourceConfig();
        ds.setUser(user);
        ds.setType(DataSourceType.GIT_LOCAL);
        ds.setName("src");
        ds.setPath("/tmp/src");
        entityManager.persist(ds);

        repoA = persistRepo(ds, "repo-a");
        repoB = persistRepo(ds, "repo-b");

        snapshot(repoA, OLD,    10.0);
        snapshot(repoA, LATEST, 24.0);
        snapshot(repoB, LATEST, 72.0);
    }

    private GitRepositoryEntity persistRepo(DataSourceConfig ds, String name) {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setDataSourceConfig(ds);
        repo.setRepoType(RepoType.LOCAL);
        repo.setName(name);
        repo.setRepoFullName("demo/" + name);
        repo.setLocalPath("/tmp/" + name);
        entityManager.persist(repo);
        return repo;
    }

    private MetricSnapshot snapshot(GitRepositoryEntity repo, LocalDate date, double value) {
        MetricSnapshot s = new MetricSnapshot();
        s.setUser(user);
        s.setTeam(null);
        s.setRepository(repo);
        s.setDate(date);
        s.setMetricType(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN);
        s.setValue(value);
        s.setPeriodFrom(date);
        s.setPeriodTo(date);
        entityManager.persist(s);
        return s;
    }

    @Test
    void findDatesByLatestCalculation_noCalculationTimes_fallsBackToDateOrder() {
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findDatesByLatestCalculation(user.getId(),
                MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN)).first().isEqualTo(LATEST);
    }

    @Test
    void findDatesByLatestCalculation_laterDateCalculatedEarlier_isNotFirst() {
        // The row whose window reaches furthest is not the row computed most recently.
        MetricSnapshot stale = snapshot(repoA, LATEST.plusDays(30), 812.0);
        stale.setCalculatedAt(Instant.parse("2026-09-21T08:01:44Z"));
        MetricSnapshot fresh = snapshot(repoA, OLD, 813.0);
        fresh.setCalculatedAt(Instant.parse("2026-09-21T09:34:28Z"));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findDatesByLatestCalculation(user.getId(),
                MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN)).first().isEqualTo(OLD);
    }

    @Test
    void findDatesByLatestCalculationForRepository_staysInsideTheRepository() {
        MetricSnapshot other = snapshot(repoB, LATEST.plusDays(30), 99.0);
        other.setCalculatedAt(Instant.parse("2026-09-21T10:00:00Z"));
        entityManager.flush();
        entityManager.clear();
        GitRepositoryEntity mRepo = entityManager.find(GitRepositoryEntity.class, repoA.getId());

        assertThat(repository.findDatesByLatestCalculationForRepository(user.getId(),
                MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, mRepo))
                .doesNotContain(LATEST.plusDays(30));
    }

    @Test
    void findPersonalByMetricTypeAndDate_latestDate_returnsEveryRepoRowAndNoOlderRow() {
        entityManager.flush();
        entityManager.clear();
        User managed = entityManager.find(User.class, user.getId());

        List<MetricSnapshot> rows = repository.findPersonalByMetricTypeAndDate(
                managed, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, LATEST);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(MetricSnapshot::getValue)
                .containsExactlyInAnyOrder(24.0, 72.0);
    }

    @Test
    void findPersonalByMetricTypeAndRepositoryAndDate_oneRepo_returnsOnlyThatReposRow() {
        entityManager.flush();
        entityManager.clear();
        User managed              = entityManager.find(User.class, user.getId());
        GitRepositoryEntity mRepo = entityManager.find(GitRepositoryEntity.class, repoB.getId());

        List<MetricSnapshot> rows = repository.findPersonalByMetricTypeAndRepositoryAndDate(
                managed, MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN, mRepo, LATEST);

        assertThat(rows).extracting(MetricSnapshot::getValue).containsExactly(72.0);
    }

    @Test
    void findDatesByLatestCalculation_metricNeverCalculated_returnsEmpty() {
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findDatesByLatestCalculation(user.getId(),
                MetricType.KNOWLEDGE_SILO_SCORE)).isEmpty();
    }
}
