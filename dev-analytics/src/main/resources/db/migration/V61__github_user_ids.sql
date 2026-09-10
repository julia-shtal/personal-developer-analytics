-- TASK - author attribution: numeric GitHub IDs for PRs, reviews and issues.
--
-- PR and review metrics matched `author_login = :login`, a free-text value the user types
-- into their profile. Three failures follow from that: the comparison is case-sensitive,
-- nothing stops two users entering the same login, and a GitHub rename splits one person
-- into two identities whose history no longer joins up.
--
-- GitHub's numeric account ID has none of those properties -- it is stable across renames,
-- unique by construction, and already present in every payload the collectors parse
-- (`user.id` on the PR list and on reviews, GHUser#getId on issues). Attribution moves to
-- the ID; the login columns stay as display values.

ALTER TABLE users ADD COLUMN github_user_id BIGINT;

-- Partial, because github_user_id is null for every user who has not linked an account and
-- a plain UNIQUE would collapse them. Enforces the ownership rule: one GitHub account maps
-- to at most one user, so a second user claiming it is rejected (409) rather than silently
-- duplicating attribution.
CREATE UNIQUE INDEX uq_users_github_user_id
    ON users (github_user_id) WHERE github_user_id IS NOT NULL;

-- All nullable: existing rows are filled by the backfill job (work item 8), and rows whose
-- author is a deleted GitHub account stay null permanently and are simply never attributed.
ALTER TABLE github_pull_requests ADD COLUMN author_github_id   BIGINT;
ALTER TABLE github_pr_reviews    ADD COLUMN reviewer_github_id BIGINT;
ALTER TABLE issues               ADD COLUMN creator_github_id  BIGINT;
ALTER TABLE issues               ADD COLUMN assignee_github_id BIGINT;

-- Replace the two V23 login indexes: the queries they served now filter on the ID column.
DROP INDEX IF EXISTS ix_github_pr_repo_login_created;
DROP INDEX IF EXISTS ix_github_pr_repo_login_merged;

CREATE INDEX ix_github_pr_repo_ghid_created
    ON github_pull_requests (repository_id, author_github_id, created_at);

CREATE INDEX ix_github_pr_repo_ghid_merged
    ON github_pull_requests (repository_id, author_github_id, merged_at);

-- Review participation and response time filter by reviewer and order by time. The existing
-- ix_github_pr_reviews_pr_submitted covers the join from the PR side, not this direction.
CREATE INDEX ix_github_pr_reviews_reviewer_submitted
    ON github_pr_reviews (reviewer_github_id, submitted_at);

-- Marks a repository whose stored records already carry the ID columns.
--
-- Seeded asymmetrically on purpose. Ingest is incremental -- commit ingest skips hashes it
-- already holds, the PR collector skips PRs whose updated_at is unchanged, and review
-- enrichment never re-runs for a PR already COMPLETE -- so a normal collection run would
-- never revisit existing rows and their new ID columns would stay null forever. Existing
-- GitHub repos therefore start NULL, which is the backfill job's work queue. Everything
-- else (local Git, and any repo created from here on) is complete by definition.
ALTER TABLE git_repositories ADD COLUMN identity_backfilled_at TIMESTAMPTZ;

UPDATE git_repositories r
SET    identity_backfilled_at = now()
WHERE  NOT EXISTS (
           SELECT 1
           FROM   data_source_configs d
           WHERE  d.id = r.data_source_id
             AND  d.type = 'GITHUB'
       );
