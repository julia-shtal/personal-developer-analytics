package com.juliashtal.devanalytics.datasource.model.dto;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;

import java.time.LocalDateTime;

public record DataSourceResponseDto(
        Long id,
        DataSourceType type,
        String name,
        String baseUrl,
        String path,
        boolean enabled,
        LocalDateTime lastSuccessSync,
        LocalDateTime createdAt,
        Long teamId,
        boolean canDelete,
        long repoCount
) {}
