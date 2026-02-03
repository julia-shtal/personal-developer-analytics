package com.juliashtal.devanalytics.datasource.model.dto;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDataSourceRequest {

    @NotNull
    private DataSourceType type;

    @NotBlank
    private String name;

    // baseUrl required for HTTP‑types (GitHub, Jira)
    private String baseUrl;

    // path required for GIT_LOCAL
    private String path;

    // token required for GitHub / Jira / GitHub Issues
    private String apiToken;
}

