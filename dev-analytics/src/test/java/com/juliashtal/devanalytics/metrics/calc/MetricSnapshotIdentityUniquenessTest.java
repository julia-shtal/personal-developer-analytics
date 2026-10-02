package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the V72 identity index: {@code findExisting} is read-then-write, so without a
 * database-level constraint two concurrent callers computing the same window could both miss it
 * and both insert.
 *
 * <p>Plain {@code @SpringBootTest} rather than {@code @DataJpaTest}: the concurrency test needs
 * two real connections committing independently, which an auto-rolled-back test transaction
 * would not observe. Fixture rows are cleaned up explicitly instead.</p>
 */
@SpringBootTest
class MetricSnapshotIdentityUniquenessTest {

    @Autowired MetricSnapshotWriter writer;
    @Autowired JdbcTemplate jdbc;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

    private Long userId;

    @BeforeEach
    void seedUser() {
        userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "snapshot-identity-" + System.nanoTime(),
                "snapshot-identity-" + System.nanoTime() + "@example.com",
                "fixture-hash");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM metric_snapshots WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void rawInsert_sameIdentityTwice_isRejectedByTheUniqueIndex() {
        insertRaw(DATE, 1.0);

        assertThatThrownBy(() -> insertRaw(DATE, 2.0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_sameIdentityTwice_overwritesRatherThanDuplicating() {
        User user = new User();
        user.setId(userId);

        writer.save(user, null, DATE, MetricType.DAILY_COMMITS_COUNT, 3.0, null, null, null);
        writer.save(user, null, DATE, MetricType.DAILY_COMMITS_COUNT, 8.0, null, null, null);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM metric_snapshots WHERE user_id = ? AND metric_type = 'DAILY_COMMITS_COUNT' "
                        + "AND date = ? AND team_id IS NULL AND repository_id IS NULL",
                Integer.class, userId, DATE);
        assertThat(count).isEqualTo(1);

        Double storedValue = jdbc.queryForObject(
                "SELECT value FROM metric_snapshots WHERE user_id = ? AND metric_type = 'DAILY_COMMITS_COUNT' "
                        + "AND date = ? AND team_id IS NULL AND repository_id IS NULL",
                Double.class, userId, DATE);
        assertThat(storedValue).isEqualTo(8.0);
    }

    @Test
    void concurrentWriters_sameIdentity_produceExactlyOneRow() throws InterruptedException {
        List<Double> values = List.of(5.0, 9.0);
        ExecutorService pool = Executors.newFixedThreadPool(values.size());
        CountDownLatch ready = new CountDownLatch(values.size());
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        User user = new User();
        user.setId(userId);

        for (double value : values) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    writer.save(user, null, DATE, MetricType.DAILY_COMMITS_COUNT, value, null, null, null);
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).as("both writers reached the start line").isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).as("both writers finished").isTrue();

        assertThat(failure.get()).isNull();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM metric_snapshots WHERE user_id = ? AND metric_type = 'DAILY_COMMITS_COUNT' "
                        + "AND date = ? AND team_id IS NULL AND repository_id IS NULL",
                Integer.class, userId, DATE);
        assertThat(count).isEqualTo(1);

        Double storedValue = jdbc.queryForObject(
                "SELECT value FROM metric_snapshots WHERE user_id = ? AND metric_type = 'DAILY_COMMITS_COUNT' "
                        + "AND date = ? AND team_id IS NULL AND repository_id IS NULL",
                Double.class, userId, DATE);
        assertThat(storedValue).isIn(values);
    }

    private void insertRaw(LocalDate date, double value) {
        jdbc.update(
                "INSERT INTO metric_snapshots (user_id, date, metric_type, value, calculated_at) "
                        + "VALUES (?, ?, 'DAILY_COMMITS_COUNT', ?, now())",
                userId, date, value);
    }
}
