package com.juliashtal.devanalytics.github.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "github_pr_reviews")
public class GitHubPrReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pr_id")
    private GitHubPullRequestEntity pullRequest;

    private String reviewerLogin;

    private String state; // APPROVED, CHANGES_REQUESTED, COMMENTED

    private Instant submittedAt;
}
