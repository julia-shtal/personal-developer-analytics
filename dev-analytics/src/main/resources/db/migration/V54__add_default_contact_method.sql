ALTER TABLE user_notification_prefs
    ADD COLUMN default_contact_method VARCHAR(16) NOT NULL DEFAULT 'IN_APP'
        CHECK (default_contact_method IN ('IN_APP', 'EMAIL'));

-- rollback: ALTER TABLE user_notification_prefs DROP COLUMN default_contact_method;
