package com.juliashtal.devanalytics.metrics.model;

import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One calendar day for which personal metrics have been computed for one user.
 *
 * <p>Records the calculation rather than inferring it from {@code metric_snapshots}: a day with
 * no activity produces no snapshot but is still computed, and an AGGREGATE snapshot's
 * {@code date} is its window start. Written exclusively by {@code MetricsService} on the
 * personal path, so the ledger cannot disagree with what was computed.</p>
 */
@Data
@Entity
@Table(
        name = "metric_coverage",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_metric_coverage_user_date",
                columnNames = {"user_id", "date"}))
public class MetricCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
