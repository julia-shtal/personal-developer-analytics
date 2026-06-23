package com.juliashtal.devanalytics.datasource.model.dto;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;

import java.time.Instant;

public record DataSourceResponseDto(
        Long id,
        DataSourceType type,
        String name,
        String baseUrl,
        String path,
        boolean enabled,
        Instant lastSuccessSync,
        Instant createdAt,
        Long teamId,
        boolean canDelete,
        long repoCount
) {}
