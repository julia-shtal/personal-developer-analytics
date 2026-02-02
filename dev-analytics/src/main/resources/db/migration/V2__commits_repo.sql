-- Git repositories table
CREATE TABLE git_repositories (
                                  id BIGSERIAL PRIMARY KEY,
                                  data_source_id BIGINT NOT NULL,
                                  name VARCHAR(255) NOT NULL,
                                  local_path VARCHAR(1024) NOT NULL,
                                  last_fetched_commit_hash VARCHAR(64),
                                  last_scan_at TIMESTAMP,
                                  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Git commits table
CREATE TABLE git_commits (
                             id BIGSERIAL PRIMARY KEY,
                             repository_id BIGINT NOT NULL,
                             hash VARCHAR(64) NOT NULL UNIQUE,
                             author_name VARCHAR(255) NOT NULL,
                             author_email VARCHAR(255) NOT NULL,
                             author_date TIMESTAMP NOT NULL,
                             message VARCHAR(4096),
                             additions INTEGER NOT NULL DEFAULT 0,
                             deletions INTEGER NOT NULL DEFAULT 0,
                             files_changed INTEGER NOT NULL DEFAULT 0,
                             parent_hash VARCHAR(64)
);

-- Foreign keys
ALTER TABLE git_repositories
    ADD CONSTRAINT fk_git_repositories_data_source
        FOREIGN KEY (data_source_id)
            REFERENCES data_source_configs (id);

ALTER TABLE git_commits
    ADD CONSTRAINT fk_git_commits_repository
        FOREIGN KEY (repository_id)
            REFERENCES git_repositories (id);


