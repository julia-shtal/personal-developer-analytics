package com.juliashtal.devanalytics.github.model;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(
        name = "github_pull_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_github_pr_repo_number",
                        columnNames = {"repository_id", "number"}
                )
        }
)
/**
 * JPA entity for github_pull_requests. One pull request with its enrichment stats.
 */
public class GitHubPullRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // link with GitRepositoryEntity (owner/repo)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepositoryEntity repository;

    // PR number in repo
    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    private String authorLogin;

    private String state;       // open, closed
    private boolean merged;
    private Long leadTimeHours;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
    private Instant mergedAt;

    private int additions;
    private int deletions;
    private int changedFiles;
    private int commentsCount;
    private int reviewCommentsCount;
    private int commitsCount;

    /**
     * Enrichment state for size stats (additions/deletions/changedFiles/commitsCount).
     * GitHub's PR list endpoint omits these fields; they require a separate detail call.
     * New PRs start as PENDING and are enriched asynchronously.
     * Merged PRs with COMPLETE stats are never re-fetched (their diff is immutable).
     * Open PRs are reset to PENDING whenever updated_at changes (their diff can grow).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatsStatus statsStatus = StatsStatus.COMPLETE;

    private Instant statsFetchedAt;

    @Column(nullable = false)
    private int statsAttempts = 0;
}
