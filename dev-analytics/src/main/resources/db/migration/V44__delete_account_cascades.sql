-- V44: Add ON DELETE CASCADE to metric_snapshots.fk_metric_snapshots_user
-- so that deleting a user removes all their personal and team-scoped metric snapshots.
-- All other FKs referencing users(id) already have ON DELETE CASCADE (V1, V11, V12,
-- V13, V20, V28, V43). This is the only missing one.

ALTER TABLE metric_snapshots
    DROP CONSTRAINT fk_metric_snapshots_user,
    ADD CONSTRAINT fk_metric_snapshots_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- rollback:
--   ALTER TABLE metric_snapshots
--       DROP CONSTRAINT fk_metric_snapshots_user,
--       ADD CONSTRAINT fk_metric_snapshots_user
--           FOREIGN KEY (user_id) REFERENCES users(id);
