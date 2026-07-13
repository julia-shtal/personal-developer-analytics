package com.juliashtal.devanalytics.issue.model;

import java.time.Instant;

/**
 * Issue as returned to the client.
 */
public record IssueDto(
        Long id,
        String sourceIssueKey,
        String title,
        String description,
        String state,
        String assignee,
        String creator,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        String labels
) {
    public static IssueDto fromEntity(IssueEntity e) {
        return new IssueDto(
                e.getId(),
                e.getSourceIssueKey(),
                e.getTitle(),
                e.getDescription(),
                e.getState(),
                e.getAssignee(),
                e.getCreator(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getClosedAt(),
                e.getLabels()
        );
    }
}

