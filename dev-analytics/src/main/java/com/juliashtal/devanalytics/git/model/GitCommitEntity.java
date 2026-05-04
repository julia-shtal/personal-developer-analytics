package com.juliashtal.devanalytics.git.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "git_commits")
public class GitCommitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "git_commit_seq")
    @SequenceGenerator(name = "git_commit_seq", sequenceName = "git_commits_id_seq", allocationSize = 500)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepositoryEntity repository;

    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    @Column(nullable = false)
    private String authorName;

    @Column(nullable = false)
    private String authorEmail;

    @Column(nullable = false)
    private Instant authorDate;

    private String message;

    private int additions;
    private int deletions;
    private int filesChanged;

    private String parentHash;

    /**
     * Enrichment state for per-commit stats (additions/deletions/filesChanged).
     * GitHub commits ingested from the list endpoint start as PENDING and are
     * enriched asynchronously via the detail endpoint.
     * Local git commits are always COMPLETE (stats come from JGit directly).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatsStatus statsStatus = StatsStatus.COMPLETE;

    /** Timestamp when stats were successfully fetched or definitively skipped/failed. */
    private Instant statsFetchedAt;

    /** Number of enrichment attempts made so far (used for backoff and give-up logic). */
    @Column(nullable = false)
    private int statsAttempts = 0;
}

