package com.juliashtal.devanalytics.git.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterLocalRepoRequest {
    @NotNull
    private Long dataSourceId;
    @NotBlank
    private String name;
    @NotBlank
    private String localPath;
}

