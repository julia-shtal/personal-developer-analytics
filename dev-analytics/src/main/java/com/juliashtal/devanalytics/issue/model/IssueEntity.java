package com.juliashtal.devanalytics.issue.model;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * JPA entity for issues. Unified GitHub or Jira issue record.
 */
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

    /** Issue key as it appears in the upstream system. GitHub: {@code owner/repo#42}; Jira: {@code PDA-123}. */
    @Column(name = "source_issue_key", nullable = false)
    private String sourceIssueKey;

    // Explicit source discriminator — set on every save path.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepositoryEntity repository;

    // Snapshot label: repo full name for GITHUB, project key for JIRA.
    // Named source_context (not repo_name) because Jira issues aren't repo-scoped.
    @Column(name = "source_context")
    private String sourceContext;

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
