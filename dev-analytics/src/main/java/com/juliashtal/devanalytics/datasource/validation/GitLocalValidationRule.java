package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Validates {@link DataSourceType#GIT_LOCAL} creation requests.
 * Mirrors DB constraint {@code chk_gitlocal_path}.
 */
@Component
public class GitLocalValidationRule implements DataSourceValidationRule {

    @Override
    public DataSourceType supports() {
        return DataSourceType.GIT_LOCAL;
    }

    @Override
    public void validate(CreateDataSourceRequest req) {
        if (req.getPath() == null || req.getPath().isBlank()) {
            throw new IllegalArgumentException("Path is required for GIT_LOCAL");
        }
        File f = new File(req.getPath());
        if (!f.exists() || !f.isDirectory()) {
            throw new IllegalArgumentException("Path does not exist or is not a directory: " + req.getPath());
        }
    }
}
