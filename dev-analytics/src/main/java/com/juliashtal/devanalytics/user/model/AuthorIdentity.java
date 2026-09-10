package com.juliashtal.devanalytics.user.model;

import java.util.Set;

/**
 * The stable identifiers a user is attributed by, resolved once per calculation run.
 *
 * <p>Three identifiers rather than one, because no single one covers every record:
 * <ul>
 *   <li>{@code commitEmails} — declared addresses. The only path for local JGit commits,
 *       which never pass through the GitHub API and so carry no account ID.</li>
 *   <li>{@code githubUserId} — GitHub's numeric account ID. Stable across login renames and
 *       unique by construction, so it is the attribution key for commits GitHub resolved,
 *       and for every PR, review and GitHub issue.</li>
 *   <li>{@code jiraAccountId} — the Jira accountId, for Jira issues.</li>
 * </ul>
 *
 * <p>Immutable and already normalised: addresses arrive trimmed and lower-cased, matching the
 * CHECK constraint on {@code user_commit_emails} and the {@code lower(author_email)} index.
 * Calculators must never normalise again or re-derive identities from names.
 *
 * <p>A missing identifier means "attribute nothing", never "attribute everything": a calculator
 * whose identifier is absent returns without writing, which is why the {@code has*} predicates
 * exist.
 */
public record AuthorIdentity(Set<String> commitEmails, Long githubUserId, String jiraAccountId) {

    public AuthorIdentity {
        commitEmails = commitEmails == null ? Set.of() : Set.copyOf(commitEmails);
    }

    /** An identity with nothing declared. Every {@code has*} predicate is false. */
    public static AuthorIdentity empty() {
        return new AuthorIdentity(Set.of(), null, null);
    }

    /** Commits need either a declared address or a GitHub account; either path alone is enough. */
    public boolean hasCommitIdentity() {
        return !commitEmails.isEmpty() || githubUserId != null;
    }

    /** PRs and reviews are matched by numeric GitHub ID only — there is no fallback to the login. */
    public boolean hasGithubIdentity() {
        return githubUserId != null;
    }

    /** Issues come from two systems; either identifier makes the calculator worth running. */
    public boolean hasIssueIdentity() {
        return githubUserId != null || jiraAccountId != null;
    }
}
