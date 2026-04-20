-- Add team_id to metric_snapshots to scope metrics per team.
-- NULL  = personal metrics (user's own data sources only).
-- NOT NULL = team-scoped metrics (repos owned by that team, attributed by author identity).
-- A user in two teams gets separate snapshot rows per team.

ALTER TABLE metric_snapshots
    ADD COLUMN team_id BIGINT REFERENCES teams(id) ON DELETE CASCADE;

CREATE INDEX ix_metric_user_team_date_type
    ON metric_snapshots(user_id, team_id, date, metric_type);
