package com.juliashtal.devanalytics.github.model;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
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
}
