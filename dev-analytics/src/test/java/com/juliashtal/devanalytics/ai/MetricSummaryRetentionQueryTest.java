package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.repository.MetricSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the nightly retention job deletes from {@code metric_summaries}: rows whose
 * {@code period_to} is before the horizon, and nothing else.
 *
 * <p>Run against the real schema (not isolated per test) because this query is deliberately
 * unscoped by user, matching the equivalent {@code metric_snapshots}/{@code metric_coverage}
 * retention queries elsewhere in this plan; row survival is pinned via {@code countFor}, scoped
 * to this fixture's own user, which is immune to unrelated pre-existing rows in the shared DB.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MetricSummaryRetentionQueryTest {

    @Autowired MetricSummaryRepository repository;
    @Autowired JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void seedUser() {
        userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) " +
                        "VALUES ('retention-summary-fixture', 'retention-summary-fixture@example.com', 'x') " +
                        "RETURNING id",
                Long.class);
    }

    @Test
    void deleteExpired_periodToBeforeHorizon_isRemoved() {
        insertSummary(userId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));

        repository.deleteExpired(LocalDate.of(2024, 6, 1));

        assertThat(countFor(userId)).isZero();
    }

    @Test
    void deleteExpired_periodToOnOrAfterHorizon_isKept() {
        insertSummary(userId, LocalDate.of(2024, 5, 26), LocalDate.of(2024, 6, 1));

        repository.deleteExpired(LocalDate.of(2024, 6, 1));

        assertThat(countFor(userId)).isEqualTo(1);
    }

    @Test
    void deleteExpired_mixOfExpiredAndCurrent_removesOnlyExpired() {
        insertSummary(userId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7));   // expired
        insertSummary(userId, LocalDate.of(2024, 5, 26), LocalDate.of(2024, 6, 1));  // current

        int removed = repository.deleteExpired(LocalDate.of(2024, 6, 1));

        // Global delete against the real, shared dev_analytics database — >= is the only safe
        // bound here; countFor below is what actually pins this fixture's correctness.
        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(countFor(userId)).isEqualTo(1);
    }

    private long countFor(Long userId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM metric_summaries WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    private void insertSummary(Long userId, LocalDate periodFrom, LocalDate periodTo) {
        jdbc.update(
                "INSERT INTO metric_summaries (user_id, period_from, period_to, scope, headline, model_name, prompt_version) "
                        + "VALUES (?, ?, ?, 'PERSONAL', 'Fixture summary', 'llama3.2', 'aaaaaaaaaaaaaaaa')",
                userId, periodFrom, periodTo);
    }
}
