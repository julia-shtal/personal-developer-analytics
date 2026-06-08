-- PDA-68 / T7: ensure every user has a notification-prefs row, defaulting all toggles off.
-- Existing rows (users who customised their prefs) are left untouched by the NOT EXISTS guard.
INSERT INTO user_notification_prefs (user_id, ai_brief, sync_failures, after_hours, new_team_member)
SELECT u.id, FALSE, FALSE, FALSE, FALSE
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM user_notification_prefs p WHERE p.user_id = u.id
);

-- Align column defaults with the opt-out policy so future inserts match the entity defaults.
ALTER TABLE user_notification_prefs ALTER COLUMN ai_brief      SET DEFAULT FALSE;
ALTER TABLE user_notification_prefs ALTER COLUMN sync_failures SET DEFAULT FALSE;
ALTER TABLE user_notification_prefs ALTER COLUMN after_hours   SET DEFAULT FALSE;

-- rollback: backfilled rows are indistinguishable from natural ones (no row rollback needed);
--           to restore prior defaults run ALTER COLUMN ... SET DEFAULT TRUE for the three columns.
