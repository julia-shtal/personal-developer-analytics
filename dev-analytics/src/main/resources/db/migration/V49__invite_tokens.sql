CREATE TABLE invite_tokens (
    id              BIGSERIAL PRIMARY KEY,
    token           VARCHAR(64)  NOT NULL UNIQUE,
    email           VARCHAR(256) NOT NULL,
    role            VARCHAR(16)  NOT NULL DEFAULT 'DEVELOPER',
    team_id         BIGINT REFERENCES teams(id) ON DELETE SET NULL,
    created_by      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at      TIMESTAMP NOT NULL,
    used_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
-- rollback: DROP TABLE invite_tokens;
