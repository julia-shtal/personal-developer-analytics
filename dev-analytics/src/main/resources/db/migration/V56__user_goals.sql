-- V56: user_goals — stores developer metric targets for the AI coaching loop.
-- metric_type stored as VARCHAR (MetricType enum name) to avoid migration coupling.
CREATE TABLE user_goals (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    metric_type VARCHAR(80) NOT NULL,
    target_value DOUBLE PRECISION NOT NULL,
    target_date DATE NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_goals_user_id ON user_goals(user_id);
