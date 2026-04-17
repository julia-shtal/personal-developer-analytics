-- Replace free-form dimensionsJson with typed period columns.
-- Lead-time and aggregate metrics store the calculation window here.
-- Daily metrics leave both columns NULL (the date column already is the window).

ALTER TABLE metric_snapshots
    ADD COLUMN period_from DATE,
    ADD COLUMN period_to   DATE;

ALTER TABLE metric_snapshots
    DROP COLUMN dimensions_json;
