package com.juliashtal.devanalytics.git.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "git_commits")
public class GitCommitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Column(length = 4096)
    private String message;

    private int additions;
    private int deletions;
    private int filesChanged;

    private String parentHash;
}

