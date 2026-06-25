package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;

/**
 * Contract for type-specific validation of a {@link CreateDataSourceRequest}.
 * Implement one bean per {@link DataSourceType}. {@link DataSourceValidationRegistry}
 * picks it up automatically.
 */
public interface DataSourceValidationRule {

    DataSourceType supports();

    /**
     * Validates the type-specific fields of the request.
     *
     * @throws IllegalArgumentException if validation fails (message is user-visible)
     */
    void validate(CreateDataSourceRequest req);
}
