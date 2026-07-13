package com.juliashtal.devanalytics.datasource.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * JPA entity for sync_jobs. Records the outcome of one collection run for a data source.
 */
@Data
@Entity
@Table(name = "sync_jobs")
public class SyncJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncJobStatus status;

    @Column(length = 100)
    private String phase;

    @Column(name = "total_processed")
    private Integer totalProcessed;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(length = 500)
    private String result;

    @Column(length = 1000)
    private String error;
}
