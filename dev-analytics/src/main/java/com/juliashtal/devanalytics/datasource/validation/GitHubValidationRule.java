package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import org.springframework.stereotype.Component;

/**
 * Validates {@link DataSourceType#GITHUB} creation requests.
 * Mirrors DB constraint {@code chk_remote_baseurl}.
 */
@Component
public class GitHubValidationRule implements DataSourceValidationRule {

    @Override
    public DataSourceType supports() {
        return DataSourceType.GITHUB;
    }

    @Override
    public void validate(CreateDataSourceRequest req) {
        HttpBaseValidation.validate(req);
    }
}
