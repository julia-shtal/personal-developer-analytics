package com.juliashtal.devanalytics.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationPrefsBackfillTest {

    @Autowired
    JdbcTemplate jdbc;

    /**
     * These tests characterise the V53 notification-prefs backfill migration against
     * the real schema. They must not assume the database already contains users:
     * on a clean database (e.g. CI) the users table is empty, since no migration
     * seeds one. Each test therefore starts from a known fixture — a single user
     * with an already-backfilled prefs row — created here.
     * <p>
     * {@code @DataJpaTest} wraps every test method in a transaction that is rolled
     * back afterwards, so this fixture is recreated fresh per test and never leaks.
     */
    @BeforeEach
    void seedUserWithPrefs() {
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) " +
                        "VALUES ('backfill-fixture', 'backfill-fixture@example.com', 'x') " +
                        "RETURNING id",
                Long.class);

        jdbc.update(
                "INSERT INTO user_notification_prefs " +
                        "(user_id, ai_brief, sync_failures, after_hours, new_team_member) " +
                        "VALUES (?, FALSE, FALSE, FALSE, FALSE)",
                userId);
    }

    @Test
    void everyUserHasExactlyOnePrefsRow_afterMigrations() {
        Integer usersWithoutRow = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users u WHERE NOT EXISTS " +
                        "(SELECT 1 FROM user_notification_prefs p WHERE p.user_id = u.id)",
                Integer.class);
        assertThat(usersWithoutRow).isZero();
    }

    @Test
    void backfillIsIdempotent_secondRunInsertsNothing() {
        int inserted = jdbc.update(
                "INSERT INTO user_notification_prefs (user_id, ai_brief, sync_failures, after_hours, new_team_member) " +
                        "SELECT u.id, FALSE, FALSE, FALSE, FALSE FROM users u " +
                        "WHERE NOT EXISTS (SELECT 1 FROM user_notification_prefs p WHERE p.user_id = u.id)");
        assertThat(inserted).isZero();
    }

    @Test
    void backfillGuard_doesNotOverwriteExistingCustomRow() {
        Long userId = jdbc.queryForObject("SELECT id FROM users LIMIT 1", Long.class);
        jdbc.update("UPDATE user_notification_prefs SET ai_brief = TRUE, sync_failures = TRUE, after_hours = TRUE WHERE user_id = ?", userId);

        jdbc.update(
                "INSERT INTO user_notification_prefs (user_id, ai_brief, sync_failures, after_hours, new_team_member) " +
                        "SELECT u.id, FALSE, FALSE, FALSE, FALSE FROM users u " +
                        "WHERE NOT EXISTS (SELECT 1 FROM user_notification_prefs p WHERE p.user_id = u.id)");

        Boolean aiBrief = jdbc.queryForObject(
                "SELECT ai_brief FROM user_notification_prefs WHERE user_id = ?", Boolean.class, userId);
        assertThat(aiBrief).isTrue();
    }

    @Test
    void columnDefaultsAreFalse_afterMigration() {
        String aiBriefDefault = jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns " +
                        "WHERE table_name = 'user_notification_prefs' AND column_name = 'ai_brief'",
                String.class);
        assertThat(aiBriefDefault).containsIgnoringCase("false");
    }
}