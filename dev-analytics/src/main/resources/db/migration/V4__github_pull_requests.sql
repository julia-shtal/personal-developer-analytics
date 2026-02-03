CREATE TABLE github_pull_requests (
                                      id BIGSERIAL PRIMARY KEY,
                                      repository_id BIGINT NOT NULL,
                                      number INT NOT NULL,
                                      title VARCHAR(255) NOT NULL,
                                      author_login VARCHAR(255),
                                      state VARCHAR(32),
                                      merged BOOLEAN NOT NULL DEFAULT FALSE,
                                      created_at TIMESTAMP,
                                      updated_at TIMESTAMP,
                                      closed_at TIMESTAMP,
                                      merged_at TIMESTAMP,
                                      additions INT NOT NULL DEFAULT 0,
                                      deletions INT NOT NULL DEFAULT 0,
                                      changed_files INT NOT NULL DEFAULT 0,
                                      comments_count INT NOT NULL DEFAULT 0,
                                      review_comments_count INT NOT NULL DEFAULT 0,
                                      commits_count INT NOT NULL DEFAULT 0,
                                      CONSTRAINT uk_github_pr_repo_number UNIQUE (repository_id, number)
);

ALTER TABLE github_pull_requests
    ADD CONSTRAINT fk_github_pr_repository
        FOREIGN KEY (repository_id)
            REFERENCES git_repositories (id);
