package com.juliashtal.devanalytics.issue.model;

/** Discriminator for {@link IssueEntity} — see ADR-005 Option C. */
public enum IssueSource {
    GITHUB,
    JIRA
}
