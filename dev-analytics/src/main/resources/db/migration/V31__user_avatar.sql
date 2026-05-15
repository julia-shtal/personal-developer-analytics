ALTER TABLE users
    ADD COLUMN avatar_data         BYTEA,
    ADD COLUMN avatar_content_type VARCHAR(32),
    ADD COLUMN avatar_preset       VARCHAR(64);
