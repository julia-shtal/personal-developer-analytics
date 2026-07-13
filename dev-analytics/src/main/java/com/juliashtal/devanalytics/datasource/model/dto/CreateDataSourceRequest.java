package com.juliashtal.devanalytics.datasource.model.dto;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request to create a data source.
 */
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

    // optional: if set, the data source is team-scoped (GITHUB/JIRA only)
    private Long teamId;

    // repo to auto-register after DS creation (GITHUB type only)
    private String repoFullName;

    // optional: Jira project key to scope collection to a single project (JIRA type)
    private String projectKey;
}

