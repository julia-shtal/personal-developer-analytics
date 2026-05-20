package com.juliashtal.devanalytics.datasource.model.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachRepoRequest(
        @NotBlank String repoFullName,
        boolean collectIssues
) {}