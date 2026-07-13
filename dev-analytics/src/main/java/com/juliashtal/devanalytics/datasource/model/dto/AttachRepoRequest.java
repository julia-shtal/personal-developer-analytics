package com.juliashtal.devanalytics.datasource.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to attach (or subscribe to) a GitHub repository under a datasource.
 */
public record AttachRepoRequest(
        @NotBlank String repoFullName,
        boolean collectIssues
) {}