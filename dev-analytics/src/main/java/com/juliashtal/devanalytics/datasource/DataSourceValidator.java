package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.model.dto.CreateDataSourceRequest;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

@Service
public class DataSourceValidator {

    public void validateCreate(CreateDataSourceRequest req) {
        if (req.getType() == null) {
            throw new IllegalArgumentException("Data source type is required");
        }

        switch (req.getType()) {
            case GIT_LOCAL -> validateGitLocal(req);
            case GITHUB, GITHUB_ISSUES, JIRA -> validateHttp(req);
        }
    }

    private void validateGitLocal(CreateDataSourceRequest req) {
        if (req.getPath() == null || req.getPath().isBlank()) {
            throw new IllegalArgumentException("Path is required for GIT_LOCAL");
        }
        File f = new File(req.getPath());
        if (!f.exists() || !f.isDirectory()) {
            throw new IllegalArgumentException("Path does not exist or is not a directory: " + req.getPath());
        }
    }

    private void validateHttp(CreateDataSourceRequest req) {
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

