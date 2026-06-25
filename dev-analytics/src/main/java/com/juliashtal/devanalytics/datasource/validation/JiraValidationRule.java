package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import org.springframework.stereotype.Component;

/**
 * Validates {@link DataSourceType#JIRA} creation requests.
 * Mirrors DB constraint {@code chk_remote_baseurl}.
 */
@Component
public class JiraValidationRule implements DataSourceValidationRule {

    @Override
    public DataSourceType supports() {
        return DataSourceType.JIRA;
    }

    @Override
    public void validate(CreateDataSourceRequest req) {
        HttpBaseValidation.validate(req);
    }
}
