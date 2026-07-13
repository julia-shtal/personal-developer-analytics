package com.juliashtal.devanalytics.datasource.model.dto;

import com.juliashtal.devanalytics.datasource.model.SyncJobStatus;

import java.time.Instant;

/**
 * Summary of a single sync job for status displays.
 */
public record SyncJobSummaryDto(
        Long id,
        SyncJobStatus status,
        String phase,
        Instant startedAt,
        Instant completedAt,
        Integer totalProcessed,
        String error
) {}
