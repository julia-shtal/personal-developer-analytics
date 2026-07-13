package com.juliashtal.devanalytics.jira.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for JiraProjectRepoMapping (jira project id + repository id).
 */
public class JiraProjectRepoMappingId implements Serializable {
    private Long jiraProject;
    private Long repository;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JiraProjectRepoMappingId that)) return false;
        return Objects.equals(jiraProject, that.jiraProject) && Objects.equals(repository, that.repository);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jiraProject, repository);
    }
}