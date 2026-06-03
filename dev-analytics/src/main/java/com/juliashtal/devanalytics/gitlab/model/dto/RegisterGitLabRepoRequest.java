package com.juliashtal.devanalytics.gitlab.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterGitLabRepoRequest {
    private Long dataSourceId;
    @NotBlank
    private String fullName;   // "namespace/project"
}
