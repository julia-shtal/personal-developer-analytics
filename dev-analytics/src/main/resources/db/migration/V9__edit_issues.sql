ALTER TABLE issues
    ADD COLUMN repository_id BIGINT;

ALTER TABLE issues
    ADD CONSTRAINT fk_issues_repository
        FOREIGN KEY (repository_id)
            REFERENCES git_repositories (id);


