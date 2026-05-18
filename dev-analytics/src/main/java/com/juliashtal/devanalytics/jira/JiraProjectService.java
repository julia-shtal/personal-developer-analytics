package com.juliashtal.devanalytics.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.JiraException;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.UserProjectRegistration;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraProjectService {

    private final RestTemplate restTemplate;
    private final SimpleTokenEncryptor tokenEncryptor;
    private final ObjectMapper objectMapper;
    private final JiraProjectRepository jiraProjectRepository;
    private final UserProjectRegistrationRepository userProjectRegistrationRepository;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Local project management
    // -------------------------------------------------------------------------

    @Transactional
    public JiraProjectEntity addProject(DataSourceConfig dataSource, String projectKey, String projectName) {
        String normalizedKey = projectKey.trim().toUpperCase();
        return jiraProjectRepository
                .findByDataSourceAndProjectKey(dataSource, normalizedKey)
                .orElseGet(() -> {
                    String resolvedName = projectName;
                    if (resolvedName == null) {
                        resolvedName = resolveProjectName(dataSource, normalizedKey);
                    }
                    JiraProjectEntity project = new JiraProjectEntity();
                    project.setDataSource(dataSource);
                    project.setProjectKey(normalizedKey);
                    project.setProjectName(resolvedName);
                    return jiraProjectRepository.save(project);
                });
    }

    private String resolveProjectName(DataSourceConfig dataSource, String projectKey) {
        try {
            return listProjects(dataSource).stream()
                    .filter(p -> projectKey.equalsIgnoreCase(p.getKey()))
                    .map(JiraProjectDto::getName)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve Jira project name for key {}: {}", projectKey, e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<JiraProjectEntity> listTrackedProjects(DataSourceConfig dataSource) {
        return jiraProjectRepository.findAllByDataSource(dataSource);
    }

    @Transactional(readOnly = true)
    public JiraProjectEntity getProjectForUser(Long projectId, Long userId) {
        JiraProjectEntity project = jiraProjectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Jira project not found: " + projectId));

        // Datasource owner can always access
        if (project.getDataSource().getUser().getId().equals(userId)) {
            return project;
        }

        // Subscribed users can also access
        User user = userRepository.getReferenceById(userId);
        if (userProjectRegistrationRepository.existsByUserAndProject(user, project)) {
            return project;
        }

        throw new ForbiddenException("Access denied to Jira project: " + projectId);
    }

    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        JiraProjectEntity project = jiraProjectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Jira project not found: " + projectId));

        if (!project.getDataSource().getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the datasource owner can remove a tracked Jira project");
        }

        jiraProjectRepository.delete(project);
    }

    public boolean isSubscribed(User user, JiraProjectEntity project) {
        return userProjectRegistrationRepository.existsByUserAndProject(user, project);
    }

    @Transactional
    public void subscribeUser(Long projectId, Long userId) {
        JiraProjectEntity project = jiraProjectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Jira project not found: " + projectId));

        User user = userRepository.getReferenceById(userId);

        if (!userProjectRegistrationRepository.existsByUserAndProject(user, project)) {
            UserProjectRegistration reg = new UserProjectRegistration();
            reg.setUser(user);
            reg.setProject(project);
            userProjectRegistrationRepository.save(reg);
        }
    }

    @Transactional
    public void unsubscribeUser(Long projectId, Long userId) {
        JiraProjectEntity project = jiraProjectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Jira project not found: " + projectId));

        User user = userRepository.getReferenceById(userId);

        userProjectRegistrationRepository.findByUserAndProject(user, project)
                .ifPresent(userProjectRegistrationRepository::delete);
    }

    // -------------------------------------------------------------------------
    // Jira API: list available projects from the remote instance
    // -------------------------------------------------------------------------

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
