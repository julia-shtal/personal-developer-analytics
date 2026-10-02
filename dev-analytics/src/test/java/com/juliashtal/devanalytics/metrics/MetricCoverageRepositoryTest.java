package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.repository.MetricCoverageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MetricCoverageRepositoryTest {

    @Autowired
    MetricCoverageRepository repository;
    @Autowired JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void seedUser() {
        userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) " +
                        "VALUES ('coverage-fixture', 'coverage-fixture@example.com', 'x') " +
                        "RETURNING id",
                Long.class);
    }

    @Test
    void markCovered_newDay_insertsRow() {
        repository.markCovered(userId, LocalDate.of(2026, 3, 1));

        List<LocalDate> dates = repository.findDatesInRange(
                userId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(dates).containsExactly(LocalDate.of(2026, 3, 1));
    }

    @Test
    void markCovered_sameDayTwice_keepsSingleRow() {
        repository.markCovered(userId, LocalDate.of(2026, 3, 1));
        repository.markCovered(userId, LocalDate.of(2026, 3, 1));

        assertThat(repository.findDatesInRange(
                userId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1))).hasSize(1);
    }

    @Test
    void findDatesInRange_excludesDaysOutsideRange() {
        repository.markCovered(userId, LocalDate.of(2026, 2, 28));
        repository.markCovered(userId, LocalDate.of(2026, 3, 15));
        repository.markCovered(userId, LocalDate.of(2026, 4, 1));

        assertThat(repository.findDatesInRange(
                userId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .containsExactly(LocalDate.of(2026, 3, 15));
    }

    @Test
    void findDatesInRange_boundaryDatesMarked_includesBothEndpoints() {
        repository.markCovered(userId, LocalDate.of(2026, 3, 1));
        repository.markCovered(userId, LocalDate.of(2026, 3, 15));
        repository.markCovered(userId, LocalDate.of(2026, 3, 31));

        assertThat(repository.findDatesInRange(
                userId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .containsExactly(
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 15),
                        LocalDate.of(2026, 3, 31));
    }

    @Test
    void deleteByUserId_removesOnlyThatUsersRows() {
        Long otherUserId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) " +
                        "VALUES ('coverage-other', 'coverage-other@example.com', 'x') " +
                        "RETURNING id",
                Long.class);
        repository.markCovered(userId, LocalDate.of(2026, 3, 1));
        repository.markCovered(otherUserId, LocalDate.of(2026, 3, 1));

        repository.deleteByUserId(userId);

        assertThat(repository.findDatesInRange(
                userId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1))).isEmpty();
        assertThat(repository.findDatesInRange(
                otherUserId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1))).hasSize(1);
    }

    @Test
    void deleteExpired_dateBeforeHorizon_isRemoved() {
        repository.markCovered(userId, LocalDate.of(2024, 1, 1));

        repository.deleteExpired(LocalDate.of(2024, 6, 1));

        assertThat(repository.findDatesInRange(userId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1)))
                .isEmpty();
    }

    @Test
    void deleteExpired_dateOnOrAfterHorizon_isKept() {
        repository.markCovered(userId, LocalDate.of(2024, 6, 1));

        repository.deleteExpired(LocalDate.of(2024, 6, 1));

        assertThat(repository.findDatesInRange(userId, LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 1)))
                .containsExactly(LocalDate.of(2024, 6, 1));
    }

    @Test
    void deleteExpired_mixAcrossUsers_removesOnlyExpiredRows() {
        Long otherUserId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) " +
                        "VALUES ('coverage-retention-other', 'coverage-retention-other@example.com', 'x') " +
                        "RETURNING id",
                Long.class);
        repository.markCovered(userId, LocalDate.of(2024, 1, 1));      // expired
        repository.markCovered(userId, LocalDate.of(2024, 6, 1));      // current
        repository.markCovered(otherUserId, LocalDate.of(2024, 1, 1)); // expired, other user

        int removed = repository.deleteExpired(LocalDate.of(2024, 6, 1));

        // Global delete against the real, shared dev_analytics database — >= is the only safe
        // bound against unrelated pre-existing rows; findDatesInRange below pins correctness.
        assertThat(removed).isGreaterThanOrEqualTo(2);
        assertThat(repository.findDatesInRange(userId, LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 1)))
                .containsExactly(LocalDate.of(2024, 6, 1));
        assertThat(repository.findDatesInRange(otherUserId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1)))
                .isEmpty();
    }
}
