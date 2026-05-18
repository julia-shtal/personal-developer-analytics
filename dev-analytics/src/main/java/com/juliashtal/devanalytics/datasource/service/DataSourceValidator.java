package com.juliashtal.devanalytics.datasource.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
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

        if (req.getTeamId() != null && req.getType() == DataSourceType.GIT_LOCAL) {
            throw new IllegalArgumentException("GIT_LOCAL data sources cannot be team-scoped (local paths are personal)");
        }

        switch (req.getType()) {
            case GIT_LOCAL -> validateGitLocal(req);
            case GITHUB, JIRA -> validateHttp(req);
        }
    }

    private void validateGitLocal(CreateDataSourceRequest req) {
        // Mirrors DB chk_gitlocal_path: path IS NOT NULL for GIT_LOCAL.
        if (req.getPath() == null || req.getPath().isBlank()) {
            throw new IllegalArgumentException("Path is required for GIT_LOCAL");
        }
        // Application-only: verify the path exists on the server filesystem.
        File f = new File(req.getPath());
        if (!f.exists() || !f.isDirectory()) {
            throw new IllegalArgumentException("Path does not exist or is not a directory: " + req.getPath());
        }
    }

    private void validateHttp(CreateDataSourceRequest req) {
        // Mirrors DB chk_remote_baseurl: base_url IS NOT NULL for non-GIT_LOCAL.
        if (req.getBaseUrl() == null || req.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("baseUrl is required for HTTP-based data sources");
        }
        // Application-only: verify the URL is well-formed before storing it.
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

