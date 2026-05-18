package com.juliashtal.devanalytics.issue.model;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "issues")
public class IssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // GitHub issues only; null for Jira issues.
    // Uniqueness enforced by partial index uq_issues_github_external in V32.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id")
    private DataSourceConfig dataSource;

    // Jira issues only; null for GitHub issues.
    // Uniqueness enforced by partial index uq_issues_jira_external in V32.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jira_project_id")
    private JiraProjectEntity jiraProject;

    @Column(name = "external_id", nullable = false)
    private String externalId;   // JIRA: "KEY-123"; GitHub: "owner/repo#123"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepositoryEntity repository;

    private String repoName;

    @Column(nullable = false)
    private String title;

    private String description;

    private String state;        // open/closed/in-progress/...

    private String assignee;
    private String creator;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

    private String labels;       // comma-separated
}
