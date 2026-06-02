CREATE TABLE messages (
    id           BIGSERIAL PRIMARY KEY,
    sender_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body         TEXT   NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    read_at      TIMESTAMP
);

CREATE INDEX ix_messages_pair  ON messages (sender_id, recipient_id, created_at);
CREATE INDEX ix_messages_inbox ON messages (recipient_id, read_at);

-- rollback: DROP TABLE messages;
