package com.juliashtal.devanalytics.git.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request to register a local Git repository by filesystem path.
 */
@Data
public class RegisterLocalRepoRequest {
    @NotNull
    private Long dataSourceId;
    @NotBlank
    private String name;
    @NotBlank
    private String localPath;
}

