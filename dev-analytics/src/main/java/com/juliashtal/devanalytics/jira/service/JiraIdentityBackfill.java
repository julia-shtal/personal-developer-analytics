package com.juliashtal.devanalytics.jira.service;

/**
 * Re-collects every Jira project so stored issues gain their reporter and assignee accountIds.
 *
 * <p>Unlike the GitHub collectors, Jira collection already re-fetches and upserts every issue on
 * each run, so no bespoke backfill is needed — running collection is the backfill. The interface
 * exists so the migration job does not reach into the {@code jira} package directly.
 */
public interface JiraIdentityBackfill {

    /** @return how many issues were re-collected across all projects */
    int recollectAllProjects();
}
