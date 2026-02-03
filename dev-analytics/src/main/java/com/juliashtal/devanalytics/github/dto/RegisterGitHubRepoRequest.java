package com.juliashtal.devanalytics.github.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterGitHubRepoRequest {
    @NotNull
    private Long dataSourceId; // GITHUB data source
    @NotBlank
    private String fullName;   // "owner/repo"
}

