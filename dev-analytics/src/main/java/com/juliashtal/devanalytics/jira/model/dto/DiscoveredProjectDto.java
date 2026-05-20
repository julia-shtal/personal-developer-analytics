package com.juliashtal.devanalytics.jira.model.dto;

public record DiscoveredProjectDto(
        String projectKey,
        String projectName,
        boolean alreadyAttached
) {}