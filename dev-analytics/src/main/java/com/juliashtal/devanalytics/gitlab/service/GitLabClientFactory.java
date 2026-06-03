package com.juliashtal.devanalytics.gitlab.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;

import static com.juliashtal.devanalytics.helper.ParsingHelper.resolveGitLabApiBase;

@Component
public class GitLabClientFactory {

    private final TokenEncryptor tokenEncryptor;

    public GitLabClientFactory(TokenEncryptor tokenEncryptor) {
        this.tokenEncryptor = tokenEncryptor;
    }

    public String getDecryptedToken(DataSourceConfig cfg) {
        return tokenEncryptor.decrypt(cfg.getApiTokenEncrypted());
    }

    public String resolveApiBase(DataSourceConfig cfg) {
        return resolveGitLabApiBase(cfg.getBaseUrl());
    }

    public HttpRequest buildRequest(String url, String token) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("PRIVATE-TOKEN", token)
                .header("Accept", "application/json")
                .GET()
                .build();
    }
}
