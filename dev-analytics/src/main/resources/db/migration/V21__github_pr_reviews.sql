CREATE TABLE github_pr_reviews (
    id             BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    pr_id          BIGINT NOT NULL REFERENCES github_pull_requests(id) ON DELETE CASCADE,
    reviewer_login VARCHAR(255),
    state          VARCHAR(32),
    submitted_at   TIMESTAMP
);

CREATE INDEX idx_pr_reviews_pr_id ON github_pr_reviews(pr_id);
