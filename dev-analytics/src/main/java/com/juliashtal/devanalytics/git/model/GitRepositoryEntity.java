package com.juliashtal.devanalytics.git.model;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;


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

    // Globally unique "owner/repo" identifier for GitHub repos (null for local repos).
    // The UNIQUE constraint is intentional (see ADR-004): one canonical row per upstream repo
    // prevents duplicate commit ingestion. Cross-datasource sharing is handled via
    // UserRepoRegistration subscriptions, not by duplicating this row.
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
