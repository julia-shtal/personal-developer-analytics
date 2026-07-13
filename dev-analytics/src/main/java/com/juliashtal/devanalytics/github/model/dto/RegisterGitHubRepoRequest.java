package com.juliashtal.devanalytics.github.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request to register or subscribe to a GitHub repository.
 */
@Data
public class RegisterGitHubRepoRequest {
    // Required only when the repo has never been registered before.
    // If the repo already exists in the system (e.g. a manager registered it for the team),
    // this can be omitted — the caller is simply subscribing to an existing repo.
    private Long dataSourceId;
    @NotBlank
    private String fullName;   // "owner/repo"
}

