package com.juliashtal.devanalytics.jira.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request to start tracking a Jira project.
 */
@Data
public class CreateTrackedJiraProjectRequest {

    @NotNull
    private Long dataSourceId;

    @NotBlank
    private String projectKey;

    private String projectName;
}
