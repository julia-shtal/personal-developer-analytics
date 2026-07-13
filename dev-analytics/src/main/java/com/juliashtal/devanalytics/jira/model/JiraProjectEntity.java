package com.juliashtal.devanalytics.jira.model;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Canonical row representing one Jira project (e.g. {@code PDA}) tracked under a
 * {@link DataSourceConfig} Jira connection.
 *
 * <h3>Canonical-Row Model</h3>
 * A Jira project is identified globally by {@code (base_url_normalized, project_key)}.
 * The database enforces {@code UNIQUE (base_url_normalized, project_key)} (constraint
 * {@code uq_jira_project_global}), so the same upstream project can have at most one
 * canonical row in the system. This mirrors the {@code git_repositories.repo_full_name UNIQUE}
 * constraint. If two users track the same {@code (baseUrl, projectKey)} on
 * separate datasources, {@link JiraProjectService#addProject}
 * returns the existing canonical row on the second call, and the caller is responsible for
 * subscribing the new user via {@link UserProjectRegistration}.
 *
 * <h3>Ownership Semantics</h3>
 * The canonical row is owned implicitly by the user who owns the {@link DataSourceConfig}
 * referenced by {@link #dataSource}. There is no separate {@code owner_user_id} column —
 * ownership is derived via {@code dataSource.user}. Only the canonical DS owner may delete
 * the project row; subscribed users are read-only with respect to the canonical row.
 *
 * <h3>Cross-DS Sharing via UserProjectRegistration</h3>
 * Users who are not the canonical owner subscribe to a project via
 * {@link UserProjectRegistration}, which mirrors how {@code UserRepoRegistration} works for
 * Git repositories. The metric engine collects project IDs from both ownership and subscription
 * paths before querying issues.
 *
 * @see JiraUrl#normalize(String) for the URL normalization rule applied to {@link #baseUrlNormalized}
 */
@Data
@Entity
@Table(
        name = "jira_projects",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_jira_project_global",
                columnNames = {"base_url_normalized", "project_key"}
        )
)
/**
 * JPA entity for jira_projects. Canonical row per upstream Jira project.
 */
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

    /** Lowercased, trailing-slash-stripped copy of {@code dataSource.baseUrl}. Enforces the global unique constraint. */
    @Column(name = "base_url_normalized", nullable = false, length = 255)
    private String baseUrlNormalized;

    @Column(name = "last_scan_at")
    private Instant lastScanAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        baseUrlNormalized = JiraUrl.normalize(dataSource.getBaseUrl());
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        baseUrlNormalized = JiraUrl.normalize(dataSource.getBaseUrl());
        updatedAt = Instant.now();
    }
}
