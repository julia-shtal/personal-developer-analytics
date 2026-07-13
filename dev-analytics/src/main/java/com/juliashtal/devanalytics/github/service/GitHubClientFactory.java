package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.stereotype.Component;

/**
 * Builds authenticated GitHub API clients, decrypting the stored token per data source.
 */
@Component
public class GitHubClientFactory {

    private final TokenEncryptor tokenEncryptor;

    public GitHubClientFactory(TokenEncryptor tokenEncryptor) {
        this.tokenEncryptor = tokenEncryptor;
    }

    /** Returns the plain-text token for the data source (needed for raw HTTP calls). */
    public String getDecryptedToken(DataSourceConfig cfg) {
        return tokenEncryptor.decrypt(cfg.getApiTokenEncrypted());
    }

    public GitHub createClient(DataSourceConfig cfg) {
        String token = tokenEncryptor.decrypt(cfg.getApiTokenEncrypted());
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GitHub token is missing for data source " + cfg.getId());
        }

        try {
            String endpoint = cfg.getBaseUrl();
            if (endpoint == null || endpoint.isBlank()) {
                endpoint = "https://api.github.com";
            }
            if (endpoint.startsWith("https://github.com")) {
                endpoint = "https://api.github.com";
            }
            return new GitHubBuilder()
                    .withEndpoint(endpoint)
                    .withOAuthToken(token)
                    .build();
        } catch (Exception e) {
            throw new GitHubException("Failed to create GitHub client", e);
        }
    }
}

