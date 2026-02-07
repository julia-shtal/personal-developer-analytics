CREATE TABLE issues (
                        id BIGSERIAL PRIMARY KEY,
                        data_source_id BIGINT NOT NULL,
                        external_id VARCHAR(255) NOT NULL,
                        title VARCHAR(255) NOT NULL,
                        description TEXT,
                        state VARCHAR(64),
                        assignee VARCHAR(255),
                        creator VARCHAR(255),
                        created_at TIMESTAMPTZ,
                        updated_at TIMESTAMPTZ,
                        closed_at TIMESTAMPTZ,
                        labels VARCHAR(1024),
                        CONSTRAINT uk_issue_source_external_id UNIQUE (data_source_id, external_id)
);

ALTER TABLE issues
    ADD CONSTRAINT fk_issues_data_source
        FOREIGN KEY (data_source_id)
            REFERENCES data_source_configs (id);

