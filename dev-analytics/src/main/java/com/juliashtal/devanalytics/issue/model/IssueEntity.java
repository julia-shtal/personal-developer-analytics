package com.juliashtal.devanalytics.issue.model;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(
        name = "issues",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_issue_source_external_id",
                        columnNames = {"data_source_id", "external_id"}
                )
        }
)
public class IssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JIRA / GITHUB_ISSUES
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id")
    private DataSourceConfig dataSource;

    @Column(name = "external_id", nullable = false)
    private String externalId;   // JIRA: "KEY-123"; GitHub: "owner/repo#123"

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

