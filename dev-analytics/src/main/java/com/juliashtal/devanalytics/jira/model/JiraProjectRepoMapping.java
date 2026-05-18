package com.juliashtal.devanalytics.jira.model;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import jakarta.persistence.*;
import lombok.Data;

/**
 * ADR-005 option C: maps a Jira project to a Git repository so that Jira
 * issues count toward that repository's metric aggregations (T4.2 wires this up).
 */
@Data
@Entity
@Table(name = "jira_project_repo_mappings")
@IdClass(JiraProjectRepoMappingId.class)
public class JiraProjectRepoMapping {

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "jira_project_id")
    private JiraProjectEntity jiraProject;

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepositoryEntity repository;
}
