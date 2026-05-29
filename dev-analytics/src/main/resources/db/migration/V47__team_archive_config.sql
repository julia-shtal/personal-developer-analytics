ALTER TABLE teams
    ADD COLUMN archived_at       TIMESTAMPTZ,
    ADD COLUMN visibility        VARCHAR(16) NOT NULL DEFAULT 'PRIVATE'
                                     CHECK (visibility IN ('PRIVATE','WORKSPACE','PUBLIC')),
    ADD COLUMN ai_brief_schedule VARCHAR(64);

-- rollback:
-- ALTER TABLE teams DROP COLUMN archived_at, DROP COLUMN visibility, DROP COLUMN ai_brief_schedule;
