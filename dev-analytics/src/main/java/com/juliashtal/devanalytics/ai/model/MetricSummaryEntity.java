package com.juliashtal.devanalytics.ai.model;

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
                        columnList = "user_id, generated_at DESC")
        }
)
public class MetricSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDate periodFrom;

    @Column(nullable = false)
    private LocalDate periodTo;

    @Column(nullable = false, length = 32)
    private String scope;

    private String repoName;

    @Column(columnDefinition = "text")
    private String overview;

    /** JSON-encoded list of insight strings. */
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
        generatedAt = Instant.now();
    }
}
