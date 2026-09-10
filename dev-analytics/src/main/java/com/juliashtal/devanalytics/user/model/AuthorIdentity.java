package com.juliashtal.devanalytics.user.model;

import java.util.Set;

/**
 * The stable identifiers a user is attributed by, resolved once per calculation run: declared
 * commit addresses, the numeric GitHub account ID, and the Jira accountId.
 *
 * <p>Immutable and already normalised, so calculators must never normalise again or re-derive an
 * identity from a name. A missing identifier means "attribute nothing", never "attribute
 * everything" — hence the {@code has*} predicates. Canonical rules: {@code
 * docs/metrics/author-attribution.md}.</p>
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
