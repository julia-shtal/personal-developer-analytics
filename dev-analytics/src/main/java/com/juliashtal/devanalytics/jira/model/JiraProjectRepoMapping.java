package com.juliashtal.devanalytics.jira.model;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

/**
 * Link table between a Jira project and a Git repository.
 * Enables the metric engine to count Jira issues toward the mapped repository's
 * issue metrics.
 */
@Data
@Entity
@Table(name = "jira_project_repo_mappings")
@IdClass(JiraProjectRepoMapping.PK.class)
public class JiraProjectRepoMapping {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jira_project_id")
    private JiraProjectEntity jiraProject;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id")
    private GitRepositoryEntity repository;

    @Data
    public static class PK implements Serializable {
        private Long jiraProject;
        private Long repository;
    }
}
