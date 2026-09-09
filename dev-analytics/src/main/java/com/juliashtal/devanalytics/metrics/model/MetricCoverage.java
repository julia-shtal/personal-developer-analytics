package com.juliashtal.devanalytics.metrics.model;

import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One calendar day for which personal metrics have been computed for one user.
 *
 * <p>This is the coverage reference the backfill subtracts from its target range. It records
 * the calculation rather than inferring it from {@code metric_snapshots}: a day with no
 * activity produces no snapshot row but is still a computed day, and an AGGREGATE snapshot's
 * {@code date} is its window start rather than the day measured. Because coverage only ever
 * grows as days are computed, a per-run cap resumes instead of truncating.
 *
 * <p>Written exclusively by {@code MetricsService} on the personal calculation path, so the
 * ledger cannot disagree with what was actually computed.
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
