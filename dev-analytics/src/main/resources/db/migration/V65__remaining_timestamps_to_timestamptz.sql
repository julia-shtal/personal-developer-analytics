-- Every Java field behind these columns is an Instant, but the columns were declared
-- TIMESTAMP WITHOUT TIME ZONE, so the stored wall clock only means anything if the reader
-- assumes the writer's zone. V55 converted four such columns; these are the rest.
--
-- The existing values are reinterpreted as UTC because that is the zone the application
-- writes in: the container runs on eclipse-temurin, whose default zone is UTC, and
-- hibernate.jdbc.time_zone now pins the binding calendar to UTC regardless of server zone.
-- Rows written by a local run on a non-UTC machine before that setting existed are shifted
-- by that machine's offset; re-run the metric backfill afterwards so any commit that moves
-- across a day boundary is recounted.

ALTER TABLE ai_conversations
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE ai_messages
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE git_commits
    ALTER COLUMN author_date       TYPE TIMESTAMPTZ USING author_date       AT TIME ZONE 'UTC',
    ALTER COLUMN stats_fetched_at  TYPE TIMESTAMPTZ USING stats_fetched_at  AT TIME ZONE 'UTC';

ALTER TABLE git_repositories
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE github_pr_reviews
    ALTER COLUMN submitted_at TYPE TIMESTAMPTZ USING submitted_at AT TIME ZONE 'UTC';

ALTER TABLE github_pull_requests
    ALTER COLUMN created_at        TYPE TIMESTAMPTZ USING created_at        AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at        TYPE TIMESTAMPTZ USING updated_at        AT TIME ZONE 'UTC',
    ALTER COLUMN closed_at         TYPE TIMESTAMPTZ USING closed_at         AT TIME ZONE 'UTC',
    ALTER COLUMN merged_at         TYPE TIMESTAMPTZ USING merged_at         AT TIME ZONE 'UTC',
    ALTER COLUMN stats_fetched_at  TYPE TIMESTAMPTZ USING stats_fetched_at  AT TIME ZONE 'UTC';

ALTER TABLE invite_tokens
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN used_at    TYPE TIMESTAMPTZ USING used_at    AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE messages
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN read_at    TYPE TIMESTAMPTZ USING read_at    AT TIME ZONE 'UTC';

ALTER TABLE password_reset_tokens
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE refresh_tokens
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE teams
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- Changing a column type rewrites the table and discards its statistics, and Postgres does not
-- re-analyze on its own. Without this the planner falls back to default estimates on the two
-- largest tables and stops choosing the partial stats indexes until autovacuum catches up.
ANALYZE git_commits;
ANALYZE github_pull_requests;
ANALYZE github_pr_reviews;
ANALYZE issues;
