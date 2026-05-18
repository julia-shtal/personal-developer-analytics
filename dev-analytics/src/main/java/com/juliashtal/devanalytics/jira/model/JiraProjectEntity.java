package com.juliashtal.devanalytics.jira.model;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(
        name = "jira_projects",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_jira_project_ds_key",
                columnNames = {"data_source_id", "project_key"}
        )
)
public class JiraProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id")
    private DataSourceConfig dataSource;

    @Column(name = "project_key", nullable = false, length = 32)
    private String projectKey;

    @Column(name = "project_name", length = 255)
    private String projectName;

    @Column(name = "last_scan_at")
    private Instant lastScanAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
