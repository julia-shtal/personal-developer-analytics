CREATE TABLE sync_jobs (
    id               BIGSERIAL    PRIMARY KEY,
    data_source_id   BIGINT       NOT NULL REFERENCES data_source_configs(id) ON DELETE CASCADE,
    status           VARCHAR(20)  NOT NULL,
    phase            VARCHAR(100),
    total_processed  INTEGER,
    started_at       TIMESTAMPTZ  NOT NULL,
    completed_at     TIMESTAMPTZ,
    result           VARCHAR(500),
    error            VARCHAR(1000)
);

CREATE INDEX idx_sync_jobs_datasource ON sync_jobs(data_source_id);
-- Partial index speeds up the startup interrupted-check, which only touches RUNNING rows.
CREATE INDEX idx_sync_jobs_running ON sync_jobs(status) WHERE status = 'RUNNING';
