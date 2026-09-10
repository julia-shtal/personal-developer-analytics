package com.juliashtal.devanalytics.attribution;

/**
 * Progress of the attribution migration, polled while a run is in flight.
 *
 * @param usersWithoutGithubId users who named a GitHub login that was never resolved to an ID
 * @param pendingRepos         repositories whose records still lack their identity columns
 * @param doneRepos            repositories already migrated
 * @param running              whether a run is in progress right now
 */
public record AttributionMigrationStatus(
        long usersWithoutGithubId,
        long pendingRepos,
        long doneRepos,
        boolean running
) {}
