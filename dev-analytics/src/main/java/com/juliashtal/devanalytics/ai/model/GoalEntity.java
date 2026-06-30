package com.juliashtal.devanalytics.ai.model;

import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Persisted developer metric goal. {@code metricType} stores the {@code MetricType} enum name
 * as a plain string so new metric types can be stored without a schema migration.
 * Validation against known enum values happens in {@code GoalService.createGoal}.
 */
@Data
@Entity
@Table(name = "user_goals")
public class GoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** MetricType enum name — validated by GoalService before persistence. */
    @Column(name = "metric_type", nullable = false, length = 80)
    private String metricType;

    @Column(name = "target_value", nullable = false)
    private double targetValue;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
