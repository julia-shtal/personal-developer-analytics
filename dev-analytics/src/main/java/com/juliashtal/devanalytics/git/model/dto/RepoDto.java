package com.juliashtal.devanalytics.git.model.dto;

/**
 * Repository list entry, including whether the current user is a subscriber.
 */
public record RepoDto(
        Long id,
        String name,
        String repoFullName,
        String localPath,
        Long dataSourceId,
        boolean subscribed,
        String repoUrl,
        boolean collectIssues,
        java.time.Instant issuesLastSyncedAt,
        Long teamId
) {}
