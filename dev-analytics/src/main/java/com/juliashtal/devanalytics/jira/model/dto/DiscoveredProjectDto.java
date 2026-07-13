package com.juliashtal.devanalytics.jira.model.dto;

/**
 * A Jira project discovered under a datasource, flagged if already attached.
 */
public record DiscoveredProjectDto(
        String projectKey,
        String projectName,
        boolean alreadyAttached
) {}