CREATE TABLE user_notification_prefs (
    user_id         BIGINT  PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    ai_brief        BOOLEAN NOT NULL DEFAULT TRUE,
    sync_failures   BOOLEAN NOT NULL DEFAULT TRUE,
    after_hours     BOOLEAN NOT NULL DEFAULT TRUE,
    new_team_member BOOLEAN NOT NULL DEFAULT FALSE
);

-- rollback: DROP TABLE user_notification_prefs;