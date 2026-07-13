package com.juliashtal.devanalytics.jira.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to attach (or subscribe to) a Jira project under a datasource.
 */
public record AttachProjectRequest(
        @NotBlank String projectKey,
        String projectName
) {}