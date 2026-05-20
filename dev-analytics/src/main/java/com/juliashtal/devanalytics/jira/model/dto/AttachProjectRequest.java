package com.juliashtal.devanalytics.jira.model.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachProjectRequest(
        @NotBlank String projectKey,
        String projectName
) {}