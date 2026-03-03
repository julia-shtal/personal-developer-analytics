CREATE TABLE metric_snapshots (
                                  id              BIGSERIAL PRIMARY KEY,
                                  user_id         BIGINT      NOT NULL,
                                  repository_id   BIGINT,
                                  date            DATE   NOT NULL,
                                  metric_type     VARCHAR(64) NOT NULL,
                                  value           DOUBLE PRECISION NOT NULL,
                                  dimensions_json TEXT,

                                  CONSTRAINT fk_metric_snapshots_user
                                      FOREIGN KEY (user_id) REFERENCES users(id),

                                  CONSTRAINT fk_metric_snapshots_repository
                                      FOREIGN KEY (repository_id) REFERENCES git_repositories(id)
);

CREATE INDEX ix_metric_user_repo_date_type
    ON metric_snapshots (user_id, repository_id, date, metric_type);

ALTER TABLE github_pull_requests
    ADD COLUMN lead_time_hours BIGINT;

