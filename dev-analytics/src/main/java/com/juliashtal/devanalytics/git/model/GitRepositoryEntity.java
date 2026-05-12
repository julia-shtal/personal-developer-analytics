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

    // for GitHub repos: "owner/repo" — globally unique, used for idempotent registration
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
