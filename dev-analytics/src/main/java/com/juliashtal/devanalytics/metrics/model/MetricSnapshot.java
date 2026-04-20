package com.juliashtal.devanalytics.metrics.model;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(
        name = "metric_snapshots",
        indexes = {
                @Index(name = "ix_metric_user_repo_date_type", columnList = "user_id, repository_id, date, metricType")
        }
)
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GitRepositoryEntity repository;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private MetricType metricType;

    @Column(nullable = false)
    private double value;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;
}

