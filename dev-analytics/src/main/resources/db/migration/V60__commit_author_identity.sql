-- TASK - author attribution: stable identities for commits.
--
-- Commit metrics matched a single address: `c.author_email = :authorEmail`, taken from
-- the account email. That address is one of several a person commits with. A commit made
-- through the GitHub web UI, from a second machine, or with email privacy enabled carries
-- a different author email and was silently dropped from every commit metric -- on the
-- reference installation, 99 of 276 commits (36%) and 42% of the churn.
--
-- Two identifiers replace the single address, because neither covers every commit:
--
--   author_github_id  GitHub's own resolution of the commit email to an account. The
--                     commit-list JSON already carries it in the top-level `author`
--                     object, which the ingest read past. It is null when the commit
--                     email belongs to no GitHub account, and always null for local
--                     JGit commits, which never pass through the GitHub API.
--
--   user_commit_emails  Addresses the user declares. The only path for local repositories,
--                     and the fallback wherever GitHub could not resolve an account.
--
-- Storing the login next to the ID keeps a display value without making attribution
-- depend on it: a login rename changes `author_github_login` and leaves the match intact.

CREATE TABLE user_commit_emails (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_commit_emails_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- Global, not per-user: one address identifies exactly one person, so a second user
    -- claiming it is a conflict to reject (409) rather than a row to insert. Without this
    -- the same commit would be attributed to two users and counted twice in team rollups.
    CONSTRAINT uq_user_commit_emails_email UNIQUE (email),

    -- Normalisation is enforced here rather than trusted from the caller, so the
    -- lower(author_email) index below is guaranteed to be comparable against these rows.
    CONSTRAINT chk_user_commit_emails_normalized
        CHECK (email = lower(btrim(email)))
);

-- The UNIQUE constraint above indexes (email); this one serves the resolver's
-- lookup by user, which is the other direction.
CREATE INDEX ix_user_commit_emails_user ON user_commit_emails (user_id);

-- Seed each existing user's account email as their first commit email, so attribution
-- after this migration is no narrower than before it. ON CONFLICT covers the case of two
-- accounts sharing an address (users.email carries no unique constraint): the first user
-- keeps it, and the second declares their own addresses in Settings.
INSERT INTO user_commit_emails (user_id, email)
SELECT id, lower(btrim(email))
FROM   users
ON CONFLICT (email) DO NOTHING;

-- Nullable: only commits ingested from the GitHub API after this migration carry these,
-- and only when GitHub resolved an account. The backfill job (work item 8) fills existing
-- rows; until it runs, the declared-email path above carries attribution on its own.
ALTER TABLE git_commits ADD COLUMN author_github_id    BIGINT;
ALTER TABLE git_commits ADD COLUMN author_github_login VARCHAR(255);

CREATE INDEX ix_git_commits_repo_ghid_date
    ON git_commits (repository_id, author_github_id, author_date);

-- Replaces the V23 index of the same name. The predicate became
-- `lower(c.author_email) IN (:emails)`, which cannot use a plain btree on author_email,
-- so the index has to be built on the same expression the query applies.
DROP INDEX IF EXISTS ix_git_commits_repo_email_date;
CREATE INDEX ix_git_commits_repo_email_date
    ON git_commits (repository_id, lower(author_email), author_date);
