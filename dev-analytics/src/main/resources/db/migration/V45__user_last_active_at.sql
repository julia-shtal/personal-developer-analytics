ALTER TABLE users ADD COLUMN last_active_at TIMESTAMP;

-- rollback: ALTER TABLE users DROP COLUMN last_active_at;
