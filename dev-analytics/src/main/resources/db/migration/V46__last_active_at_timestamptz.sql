-- V45 created last_active_at as TIMESTAMP WITHOUT TIME ZONE.
-- PostgreSQL stores the session-local value; Hibernate (JVM timezone may differ)
-- reads it back as a different UTC instant, making the timestamp appear in the future
-- from the browser and causing timeAgo() to always return "just now".
-- Changing to TIMESTAMPTZ stores UTC unambiguously and fixes the diff calculation.
UPDATE users SET last_active_at = NULL;
ALTER TABLE users ALTER COLUMN last_active_at TYPE TIMESTAMPTZ;

-- rollback: ALTER TABLE users ALTER COLUMN last_active_at TYPE TIMESTAMP; UPDATE users SET last_active_at = NULL;
