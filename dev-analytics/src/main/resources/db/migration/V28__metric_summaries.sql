CREATE TABLE metric_summaries
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    period_from      DATE         NOT NULL,
    period_to        DATE         NOT NULL,
    scope            VARCHAR(32)  NOT NULL,
    repo_name        VARCHAR(255),
    overview         TEXT,
    insights         TEXT,
    recommendations  TEXT,
    raw_model_output TEXT,
    model_name       VARCHAR(64),
    generated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_metric_summaries_user_generated
    ON metric_summaries (user_id, generated_at DESC);
