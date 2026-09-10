package com.juliashtal.devanalytics.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.GitHubException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static com.juliashtal.devanalytics.helper.ParsingHelper.resolveApiBase;

/**
 * {@link GitHubAccountLookup} over GitHub's REST API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubAccountLookupImpl implements GitHubAccountLookup {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final DataSourceConfigRepository dataSourceConfigRepository;
    private final GitHubClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<GitHubAccount> findByLogin(Long userId, String login) {
        if (login == null || login.isBlank()) return Optional.empty();

        // The user's data source supplies the Enterprise base URL and a token; neither is required.
        DataSourceConfig config = ownGithubSource(userId).orElse(null);
        String apiBase = resolveApiBase(config != null ? config.getBaseUrl() : null);
        String token   = config != null ? clientFactory.getDecryptedToken(config) : null;

        String url = apiBase + "/users/" + URLEncoder.encode(login.trim(), StandardCharsets.UTF_8);
        HttpResponse<String> response = send(url, token, "resolve GitHub login");

        // Only 404 means "no such account"; answering it for 403/401/5xx would unlink a correct identity.
        if (response.statusCode() == 404) return Optional.empty();
        if (response.statusCode() != 200) {
            throw new GitHubException("GitHub returned " + response.statusCode() + " while resolving a login");
        }
        return Optional.of(parseAccount(response.body()));
    }

    @Override
    public GitHubAccount whoAmI(DataSourceConfig config) {
        String apiBase = resolveApiBase(config.getBaseUrl());
        String token = clientFactory.getDecryptedToken(config);
        if (token == null || token.isBlank()) {
            throw new GitHubException("GitHub token is missing for data source " + config.getId());
        }

        HttpResponse<String> response = send(apiBase + "/user", token, "identify token owner");
        if (response.statusCode() != 200) {
            throw new GitHubException("GitHub returned " + response.statusCode() + " for /user");
        }
        return parseAccount(response.body());
    }

    private Optional<DataSourceConfig> ownGithubSource(Long userId) {
        if (userId == null) return Optional.empty();
        return dataSourceConfigRepository.findAllByUserId(userId).stream()
                .filter(c -> c.getType() == DataSourceType.GITHUB)
                .filter(c -> c.getApiTokenEncrypted() != null && !c.getApiTokenEncrypted().isBlank())
                .findFirst();
    }

    private GitHubAccount parseAccount(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode id = node.path("id");
            if (!id.isNumber()) {
                throw new GitHubException("GitHub account response carried no numeric id");
            }
            return new GitHubAccount(id.asLong(), node.path("login").asText(null));
        } catch (GitHubException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubException("Could not parse the GitHub account response", e);
        }
    }

    private HttpResponse<String> send(String url, String token, String context) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        try {
            return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("Interrupted while trying to " + context, e);
        } catch (Exception e) {
            throw new GitHubException("Could not reach GitHub to " + context, e);
        }
    }
}
