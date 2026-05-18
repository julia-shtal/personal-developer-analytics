package com.juliashtal.devanalytics.jira.model.dto;

import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;

import java.time.Instant;

public record JiraProjectResponseDto(
        Long id,
        Long dataSourceId,
        String projectKey,
        String projectName,
        Instant lastScanAt,
        boolean subscribed
) {
    public static JiraProjectResponseDto from(JiraProjectEntity e, boolean subscribed) {
        return new JiraProjectResponseDto(
                e.getId(),
                e.getDataSource().getId(),
                e.getProjectKey(),
                e.getProjectName(),
                e.getLastScanAt(),
                subscribed
        );
    }
}
