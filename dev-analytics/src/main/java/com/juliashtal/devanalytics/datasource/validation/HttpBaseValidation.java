package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Shared URL + token validation for HTTP-based data sources.
 * Used by {@link GitHubValidationRule} and {@link JiraValidationRule} to avoid duplication.
 */
final class HttpBaseValidation {

    private HttpBaseValidation() {}

    static void validate(CreateDataSourceRequest req) {
        if (req.getBaseUrl() == null || req.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("baseUrl is required for HTTP-based data sources");
        }
        try {
            new URL(req.getBaseUrl());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid baseUrl: " + req.getBaseUrl());
        }
        if (req.getApiToken() == null || req.getApiToken().isBlank()) {
            throw new IllegalArgumentException("apiToken is required for HTTP-based data sources");
        }
    }
}
