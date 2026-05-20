package com.juliashtal.devanalytics.git.model;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;


/**
 * Represents a single Git repository tracked by the platform.
 *
 * <h3>Content-Addressed Access Model (ADR-004)</h3>
 * <p>The platform uses a <em>canonical-row</em> model for GitHub repositories:
 * <ul>
 *   <li><strong>One canonical commit history per upstream repository.</strong>
 *       {@code repo_full_name} carries a global {@code UNIQUE} constraint so the
 *       same {@code owner/repo} is stored exactly once, regardless of how many
 *       users track it. This mirrors Git's content-addressed object store —
 *       identical content has one identity.</li>
 *   <li><strong>Cross-user access via {@link UserRepoRegistration}, not row duplication.</strong>
 *       When a second user wants to track a repository that is already registered under
 *       another user's datasource, the attach endpoint inserts a
 *       {@code user_repo_registrations} row instead of duplicating
 *       {@code git_repositories} or {@code git_commits}.</li>
 *   <li><strong>{@code git_commits.hash UNIQUE} enforces deduplication at the storage layer.</strong>
 *       Even if the canonical-row check were bypassed, duplicate commits cannot be
 *       inserted — consistent with how Git identifies objects by their SHA-1 hash.</li>
 * </ul>
 *
 * <p>See {@code docs/adr/ADR-004-cross-ds-repo-sharing.md} for the full decision record.
 */
@Data
@Entity
@Table(
        name = "git_repositories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_git_repo_datasource_path",
                        columnNames = {"data_source_id", "local_path"}
                )
        }
)
public class GitRepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id")
    private DataSourceConfig dataSourceConfig;

    @Column(nullable = false)
    private String name;

    // for local repo: path to .git; null for GitHub/remote repos
    @Column(nullable = true)
    private String localPath;

    // Explicit type discriminator — set on every save path; never inferred from null checks.
    // See ADR-003 for the decision to use a discriminator column over split tables.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepoType repoType;

    /**
     * Globally unique {@code "owner/repo"} identifier for GitHub repos; {@code null} for local repos.
     * The {@code UNIQUE} constraint is intentional (ADR-004): one canonical row per upstream
     * repository prevents duplicate commit ingestion. Cross-datasource sharing is handled via
     * {@link UserRepoRegistration} subscriptions — never by duplicating this row.
     */
    @Column(unique = true)
    private String repoFullName;

    // whether issues should be fetched during GitHub sync for this repo
    @Column(name = "collect_issues", nullable = false)
    private boolean collectIssues = false;

    @Column(name = "issues_last_synced_at")
    private Instant issuesLastSyncedAt;

    // for incremental collecting
    private String lastFetchedCommitHash;

    private LocalDateTime lastScanAt;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
