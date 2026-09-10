-- TASK - author attribution: Jira accountId.
--
-- Jira issues could not be attributed at all. The collector narrowed the fetch itself --
-- buildJql filtered on `assignee = <token owner's accountId>` -- and the canonical project
-- row is collected with whichever user's token owns it, so every subscriber to that project
-- was credited with the owner's issues. Storage made it unrecoverable after the fact: only
-- display names were kept, and a display name is neither unique nor stable.
--
-- Jira's accountId is the stable identifier (a GDPR-era opaque string, up to 128 chars).
-- Storing it per issue moves the filter out of the JQL and into the metric queries, where
-- it can differ per user; the collector then fetches the whole project once, as it should.

ALTER TABLE users ADD COLUMN jira_account_id VARCHAR(128);

-- Partial for the same reason as uq_users_github_user_id: null means "not linked", and
-- those rows must not collide. One Jira account maps to at most one user.
CREATE UNIQUE INDEX uq_users_jira_account_id
    ON users (jira_account_id) WHERE jira_account_id IS NOT NULL;

-- Nullable: filled by re-collection (work item 8 step 3). Until then Jira issues match
-- nobody, which is the correct failure direction -- previously they matched everybody.
ALTER TABLE issues ADD COLUMN assignee_account_id VARCHAR(128);
ALTER TABLE issues ADD COLUMN reporter_account_id VARCHAR(128);

-- Scoped by project, mirroring the metric queries: closed-issue and lead-time metrics
-- filter by assignee, created-issue metrics by reporter.
CREATE INDEX ix_issues_jira_project_assignee
    ON issues (jira_project_id, assignee_account_id);

CREATE INDEX ix_issues_jira_project_reporter
    ON issues (jira_project_id, reporter_account_id);
