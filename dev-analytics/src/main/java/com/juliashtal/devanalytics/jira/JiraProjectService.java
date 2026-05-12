package com.juliashtal.devanalytics.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.JiraException;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraProjectService {

    private final RestTemplate restTemplate;
    private final SimpleTokenEncryptor tokenEncryptor;
    private final ObjectMapper objectMapper;

    /**
     * Returns all Jira projects accessible from the configured Atlassian account.
     * Calls GET /rest/api/3/project/search with Basic Auth and paginates until exhausted.
     */
    public List<JiraProjectDto> listProjects(DataSourceConfig config) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required on the Jira data source");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String decryptedToken = tokenEncryptor.decrypt(config.getApiTokenEncrypted());
        String[] parts = decryptedToken.split(":", 2);
        headers.setBasicAuth(parts[0], parts[1]);

        String searchUrl = baseUrl + "/rest/api/3/project/search";
        List<JiraProjectDto> result = new ArrayList<>();
        int startAt = 0;
        int total = Integer.MAX_VALUE;

        while (startAt < total) {
            URI uri = UriComponentsBuilder.fromUriString(searchUrl)
                    .queryParam("startAt", startAt)
                    .queryParam("maxResults", 50)
                    .build().toUri();

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new JiraException("Jira project list API error: " + response.getStatusCode());
            }

            Page page = parsePage(response.getBody());
            total = page.total;
            if (page.values == null || page.values.isEmpty()) break;

            result.addAll(page.values);
            startAt += page.values.size();
            log.debug("Jira projects fetched: {}/{}", result.size(), total);
        }

        log.info("Jira project listing complete: {} projects from {}", result.size(), baseUrl);
        return result;
    }

    private Page parsePage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            int total = root.path("total").asInt(0);
            List<JiraProjectDto> values = new ArrayList<>();
            JsonNode valuesNode = root.path("values");
            if (valuesNode.isArray()) {
                for (JsonNode n : valuesNode) {
                    values.add(new JiraProjectDto(
                            n.path("key").asText(null),
                            n.path("name").asText(null),
                            n.path("id").asText(null)
                    ));
                }
            }
            return new Page(total, values);
        } catch (JsonProcessingException e) {
            throw new JiraException("Failed to parse Jira project search response", e);
        }
    }

    private record Page(int total, List<JiraProjectDto> values) {}

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraProjectDto {
        private final String key;
        private final String name;
        private final String id;
    }
}
