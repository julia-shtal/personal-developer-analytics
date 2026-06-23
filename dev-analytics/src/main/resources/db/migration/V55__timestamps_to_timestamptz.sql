-- QF-3: replace TIMESTAMP (no timezone) with TIMESTAMPTZ on the four columns
-- that were storing LocalDateTime. Instant-based Java fields require TIMESTAMPTZ
-- so Postgres preserves timezone context and comparisons are unambiguous.

ALTER TABLE data_source_configs
    ALTER COLUMN last_success_sync TYPE TIMESTAMPTZ USING last_success_sync AT TIME ZONE 'UTC',
    ALTER COLUMN created_at        TYPE TIMESTAMPTZ USING created_at        AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at        TYPE TIMESTAMPTZ USING updated_at        AT TIME ZONE 'UTC';

ALTER TABLE git_repositories
    ALTER COLUMN last_scan_at TYPE TIMESTAMPTZ USING last_scan_at AT TIME ZONE 'UTC';

-- rollback:
-- ALTER TABLE data_source_configs
--     ALTER COLUMN last_success_sync TYPE TIMESTAMP USING last_success_sync AT TIME ZONE 'UTC',
--     ALTER COLUMN created_at        TYPE TIMESTAMP USING created_at        AT TIME ZONE 'UTC',
--     ALTER COLUMN updated_at        TYPE TIMESTAMP USING updated_at        AT TIME ZONE 'UTC';
-- ALTER TABLE git_repositories
--     ALTER COLUMN last_scan_at TYPE TIMESTAMP USING last_scan_at AT TIME ZONE 'UTC';
