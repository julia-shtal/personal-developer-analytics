package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.validation.DataSourceValidationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Validates a {@link CreateDataSourceRequest} by delegating to the
 * {@link DataSourceValidationRegistry}. Keeps the team-scope pre-check
 * (GIT_LOCAL cannot be team-scoped) as a cross-cutting concern.
 */
@Service
@RequiredArgsConstructor
public class DataSourceValidator {

    private final DataSourceValidationRegistry registry;

    public void validateCreate(CreateDataSourceRequest req) {
        if (req.getType() == null) {
            throw new IllegalArgumentException("Data source type is required");
        }
        if (req.getTeamId() != null && req.getType() == DataSourceType.GIT_LOCAL) {
            throw new IllegalArgumentException("GIT_LOCAL data sources cannot be team-scoped (local paths are personal)");
        }
        registry.forType(req.getType()).validate(req);
    }
}
