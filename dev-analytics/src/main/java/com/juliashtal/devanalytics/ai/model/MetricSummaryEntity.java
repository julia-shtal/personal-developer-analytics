package com.juliashtal.devanalytics.ai.model;

import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Entity
@Table(
        name = "metric_summaries",
        indexes = {
                @Index(name = "ix_metric_summaries_user_generated",
                        columnList = "user_id, generated_at DESC"),
                @Index(name = "ix_metric_summaries_team_generated",
                        columnList = "team_id, generated_at DESC")
        }
)
public class MetricSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for team-scoped summaries. */
    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Null for personal/repository-scoped summaries. */
    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false)
    private LocalDate periodFrom;

    @Column(nullable = false)
    private LocalDate periodTo;

    @Column(nullable = false, length = 32)
    private String scope;

    /** Snapshot-in-time scope label: repo full name, Jira project key, or team name. */
    @Column(name = "context_repo_name")
    private String contextRepoName;

    @Column(columnDefinition = "text")
    private String headline;

    @Column(columnDefinition = "text")
    private String overview;

    /** JSON-encoded list of InsightDto objects. */
    @Column(columnDefinition = "text")
    private String insights;

    /** JSON-encoded list of recommendation strings. */
    @Column(columnDefinition = "text")
    private String recommendations;

    @Column(columnDefinition = "text")
    private String rawModelOutput;

    @Column(length = 64)
    private String modelName;

    @Column(nullable = false)
    private Instant generatedAt;

    @PrePersist
    public void prePersist() {
        if (generatedAt == null) generatedAt = Instant.now();
    }
}
