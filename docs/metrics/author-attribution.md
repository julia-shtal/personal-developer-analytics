# Author Attribution

**Applies to:** every metric in this directory
**Data source(s):** users, user_commit_emails, git_commits, github_pull_requests, github_pr_reviews, issues

This page is the canonical definition of *how a record is decided to belong to a user*. Each metric
document states which rule it uses and refers here for the rule itself, so the definition exists
once rather than in nineteen partial copies.

## Principle

A record is attributed through an identifier the user controls and that the upstream system
guarantees is stable. Display names are never used for matching: a Git author name, a GitHub login
and a Jira display name are all free text, none is unique, and all three can change without the
underlying account changing.

## Identifiers

| Identifier | Stored as | Supplied by |
|---|---|---|
| Declared commit addresses | `user_commit_emails.email` | the user, in Settings; seeded once from the account address at registration |
| GitHub account | `users.github_user_id` | resolved from the login via `GET /users/{login}`, or from the user's own collection token via `GET /user` |
| Jira account | `users.jira_account_id` | Jira's `accountId`, filled in when the user connects Jira with their own token |

Addresses are normalised to `lower(btrim(...))` once, when stored, and a database `CHECK`
constraint enforces it. Numeric and opaque identifiers are used as-is.

## Rules by record type

| Records | Match |
|---|---|
| `git_commits` | `author_github_id = user.githubUserId` **OR** `lower(author_email) IN user.commitEmails` |
| `github_pull_requests` | `author_github_id = user.githubUserId` |
| `github_pr_reviews` | `reviewer_github_id = user.githubUserId`; the self-review exclusion compares IDs, not logins |
| `issues`, created | GitHub: `creator_github_id = user.githubUserId`; Jira: `reporter_account_id = user.jiraAccountId` |
| `issues`, closed | GitHub: `assignee_github_id = user.githubUserId`; Jira: `assignee_account_id = user.jiraAccountId` |

### Why commits need two paths

Neither identifier covers every commit.

`author_github_id` is GitHub's own resolution of the commit address to an account, taken from the
top-level `author` object of the commit-list response. It is `null` for commits collected from a
local repository, which never pass through the GitHub API, and `null` when the address belongs to
no GitHub account.

Declared addresses are the only path for local repositories, but they miss commits made with an
address the user never registered — most commonly the `<id>+<login>@users.noreply.github.com`
alias GitHub substitutes for commits authored through its web interface.

A commit satisfying both branches is counted once: the predicate is a disjunction over one row,
not a union of two queries.

## Ownership

Each identifier belongs to exactly one user, enforced by a `UNIQUE` constraint on
`user_commit_emails.email` and by partial unique indexes on `users.github_user_id` and
`users.jira_account_id`. A second user claiming an identifier is rejected with HTTP 409. Without
this rule the same record would be attributed twice and double-counted in team aggregates.

There is no name matching, no similarity matching, and no fallback from an ID to a login.

## Missing identifiers

A calculator whose required identifier is absent writes nothing at all. It does not fall back to a
looser match and does not write zero-valued snapshots.

- Commit metrics need at least one declared address **or** a GitHub account.
- PR and review metrics need a GitHub account.
- Issue metrics need a GitHub account or a Jira account; each source is matched only by its own
  identifier, so a user linked to one system matches only that system's issues.

Attributing nothing is the deliberate failure direction. The alternative — attributing everything
in scope — is what previously credited every subscriber of a repository with every issue in it.

## Changing an identity

Adding or removing an address, linking a GitHub account, or setting a Jira accountId invalidates
every metric already stored for that user, because all of them were computed under the previous
identity. The user's snapshots and coverage ledger are therefore deleted and recomputed from
source records rather than recalculated in place: calculators only ever upsert days that produced
data, so rows the old identity produced would otherwise survive, and the coverage ledger would
mark those days computed and stop the backfill from revisiting them.

Team-scoped rows are deleted along with personal ones and are rebuilt the next time the team
manager runs a team calculation.

Renaming an account on GitHub is **not** an identity change. The numeric ID is unchanged, so the
stored login is refreshed for display and no metric is recomputed.

## Records collected before attribution existed

Ingest is incremental at every level, so the identity columns on existing rows are not filled by
an ordinary collection run. `AttributionMigrationService` (`POST /api/admin/attribution/migrate`,
ADMIN) walks each repository once and fills them, then recomputes metrics — but only once no
repository is still pending, so metrics are never rebuilt over a partly attributed history.

## Bot exclusion

Bot filtering is applied in each metric formula, not at ingestion; ingestion preserves raw signal
for audit. Whether a formula needs an explicit filter follows from what its attribution predicate
matches on.

Pull requests, reviews and issues match on a numeric account identifier alone —
`author_github_id`, `reviewer_github_id`, `creator_github_id`, `assignee_github_id`, or a Jira
`accountId`. A bot holds its own account and its own identifier, which no user shares, so it
cannot satisfy the predicate. Exclusion is implicit and no filter is written; adding one would be
a no-op that costs index selectivity while implying a safeguard that does no work.

Commits are the exception. Their predicate is a disjunction, and the email branch —
`lower(author_email) IN user.commitEmails` — is satisfiable by an automation account configured
with the user's own address. Local commits collected through JGit carry no `author_github_id` at
all, so that branch is the only one available for them. Commit-backed formulas therefore filter
explicitly on `author_name NOT LIKE '%[bot]%'`. The column is `NOT NULL`, so the predicate needs
no null guard.

Each metric document states which of the two cases applies to it.
