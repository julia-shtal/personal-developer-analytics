-- The LLM runtime version that produced a summary, read from the runtime at generation time.
--
-- Existing rows predate this read and carry no such value, so they are backfilled with a
-- placeholder rather than left null, keeping the column queryable without a null-check.

ALTER TABLE metric_summaries ADD COLUMN runtime_version VARCHAR(64);

UPDATE metric_summaries SET runtime_version = 'UNKNOWN' WHERE runtime_version IS NULL;

-- rollback:
-- ALTER TABLE metric_summaries DROP COLUMN IF EXISTS runtime_version;
