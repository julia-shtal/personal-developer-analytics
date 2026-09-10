package com.juliashtal.devanalytics.git.model;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;


/**
 * Represents a single Git repository tracked by the platform.
 *
 * <p>One canonical row per upstream repository: {@code repo_full_name} carries a global
 * {@code UNIQUE} constraint so the same {@code owner/repo} is stored exactly once regardless
 * of how many users track it. Cross-user access is handled via {@link UserRepoRegistration}
 * subscriptions — never by duplicating this row.
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
/**
 * JPA entity for git_repositories. Canonical row per local or GitHub repository.
 */
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
    // Using a discriminator column avoids split tables and keeps queries simple.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepoType repoType;

    /**
     * Globally unique {@code "owner/repo"} identifier for GitHub repos; {@code null} for local repos.
     * The {@code UNIQUE} constraint prevents duplicate commit ingestion. Cross-datasource sharing
     * is handled via {@link UserRepoRegistration} subscriptions — never by duplicating this row.
     */
    @Column(unique = true)
    private String repoFullName;

    // whether issues should be fetched during GitHub sync for this repo
    @Column(name = "collect_issues", nullable = false)
    private boolean collectIssues = false;

    @Column(name = "issues_last_synced_at")
    private Instant issuesLastSyncedAt;

    /**
     * When this repository's stored records were confirmed to carry the numeric identity
     * columns. Initialised for every newly created repository, because ingest has captured
     * those IDs from the first page onwards.
     *
     * <p>Null marks a repository collected before author attribution existed, whose rows
     * incremental ingest would never revisit. {@code AttributionMigrationService} uses null as
     * its work queue and stamps this only once every sub-step succeeded.</p>
     */
    @Column(name = "identity_backfilled_at")
    private Instant identityBackfilledAt = Instant.now();

    // for incremental collecting
    private String lastFetchedCommitHash;

    private Instant lastScanAt;

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
