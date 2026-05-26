package com.juliashtal.devanalytics.jira.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.JiraException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepository;
import com.juliashtal.devanalytics.jira.model.JiraUrl;
import com.juliashtal.devanalytics.jira.repository.UserProjectRegistrationRepository;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.UserProjectRegistration;
import com.juliashtal.devanalytics.jira.model.dto.DiscoveredProjectDto;
import com.juliashtal.devanalytics.jira.model.dto.JiraProjectResponseDto;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final DataSourceConfigRepository dataSourceConfigRepository;

    // -------------------------------------------------------------------------
    // Local project management
    // -------------------------------------------------------------------------

    /**
     * Returns the canonical {@link JiraProjectEntity} for the given datasource + project key,
     * creating one if it does not yet exist. Three-branch logic mirrors the GitHub attach path:
     * <ol>
     *   <li>Canonical row already exists under <em>this</em> datasource → return it (idempotent).</li>
     *   <li>Canonical row exists under a <em>different</em> datasource → return the existing row
     *       without inserting. Caller is responsible for subscribing the user.</li>
     *   <li>No canonical row → create a new row under this datasource.</li>
     * </ol>
     */
    @Transactional
    public JiraProjectEntity addProject(DataSourceConfig dataSource, String projectKey, String projectName) {
        String normalizedKey  = projectKey.trim().toUpperCase();
        String normalizedBase = JiraUrl.normalize(dataSource.getBaseUrl());

        // Global lookup: honours the UNIQUE(base_url_normalized, project_key) constraint.
        Optional<JiraProjectEntity> existing =
                jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(normalizedBase, normalizedKey);

        if (existing.isPresent()) {
            JiraProjectEntity project = existing.get();
            if (!project.getDataSource().getId().equals(dataSource.getId())) {
                log.info("Jira project {}:{} already registered under datasource {} — returning canonical row",
                        normalizedBase, normalizedKey, project.getDataSource().getId());
            }
            return project;
        }

        String resolvedName = projectName;
        if (resolvedName == null) {
            resolvedName = resolveProjectName(dataSource, normalizedKey);
        }
        JiraProjectEntity project = new JiraProjectEntity();
        project.setDataSource(dataSource);
        project.setProjectKey(normalizedKey);
        project.setProjectName(resolvedName);
        return jiraProjectRepository.save(project);
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
    // Subscription-based datasource access helpers (used by DataSourceService)
    // -------------------------------------------------------------------------

    /** Finds an existing tracked Jira project by Jira instance URL + project key. */
    @Transactional(readOnly = true)
    public java.util.Optional<JiraProjectEntity> findByBaseUrlAndProjectKey(String baseUrl, String projectKey) {
        return jiraProjectRepository.findByBaseUrlNormalizedAndProjectKey(
                JiraUrl.normalize(baseUrl), projectKey.trim().toUpperCase());
    }

    /**
     * Returns all tracked projects for a given Jira instance (any project key).
     * Used by {@code DataSourceService.create} to detect whether a DS for this Jira base URL
     * already exists before allowing a duplicate to be created.
     */
    @Transactional(readOnly = true)
    public List<JiraProjectEntity> findProjectsByBaseUrl(String baseUrl) {
        return jiraProjectRepository.findAllByBaseUrlNormalized(JiraUrl.normalize(baseUrl));
    }

    /** DataSourceConfigs the user can access via Jira project subscriptions. */
    @Transactional(readOnly = true)
    public List<DataSourceConfig> findSubscribedDataSourceConfigs(Long userId) {
        return userProjectRegistrationRepository.findDataSourceConfigsByUserId(userId);
    }

    /** True when the user has at least one Jira project subscription under the given datasource. */
    @Transactional(readOnly = true)
    public boolean hasSubscriptionForDataSource(Long userId, Long dataSourceId) {
        return userProjectRegistrationRepository.existsByUserIdAndDataSourceId(userId, dataSourceId);
    }

    // -------------------------------------------------------------------------
    // Controller-facing methods (datasource/{id}/projects endpoints)
    // -------------------------------------------------------------------------

    /** Lists tracked projects for a datasource. Owner and subscribed users can call this. */
    @Transactional(readOnly = true)
    public List<JiraProjectResponseDto> listProjectsForDataSource(Long userId, Long dataSourceId) {
        DataSourceConfig cfg = resolveJiraDs(userId, dataSourceId);
        User user = userRepository.getReferenceById(userId);
        return jiraProjectRepository.findAllByDataSource(cfg).stream()
                .map(p -> JiraProjectResponseDto.from(p, isSubscribed(user, p)))
                .toList();
    }

    /**
     * Attaches a Jira project to a datasource (owner only, idempotent).
     * Auto-subscribes the caller so they can see the project's metrics immediately.
     *
     * <p>Cross-DS case: if the canonical row for {@code (baseUrl, projectKey)} already belongs
     * to a different datasource, the user is subscribed to that canonical project and the calling
     * datasource is deleted if it has no canonical projects of its own (i.e. it was just created
     * and would otherwise become an empty orphan). The returned DTO reflects the canonical project,
     * whose {@code dataSourceId} may differ from the requested {@code dataSourceId}. Callers should
     * check this field and refresh their datasource list when they differ.
     */
    @Transactional
    public JiraProjectResponseDto attachProject(Long userId, Long dataSourceId, String projectKey, String projectName) {
        DataSourceConfig cfg = resolveOwnerJiraDs(userId, dataSourceId);
        JiraProjectEntity project = addProject(cfg, projectKey, projectName);
        subscribeUser(project.getId(), userId);

        if (!project.getDataSource().getId().equals(cfg.getId())
                && jiraProjectRepository.findAllByDataSource(cfg).isEmpty()) {
            // The canonical project lives under another DS and the calling DS is now empty —
            // delete the orphaned DS so the user's list shows only the canonical one.
            log.info("Deleting empty orphaned Jira DS {} after cross-DS attach to canonical DS {}",
                    cfg.getId(), project.getDataSource().getId());
            dataSourceConfigRepository.delete(cfg);
        }

        return JiraProjectResponseDto.from(project, true);
    }

    /** Removes a tracked Jira project from a datasource (owner only). Cascades issues via FK. */
    @Transactional
    public void detachProject(Long userId, Long dataSourceId, Long projectId) {
        DataSourceConfig cfg = resolveOwnerJiraDs(userId, dataSourceId);
        JiraProjectEntity project = jiraProjectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Jira project not found: " + projectId));
        if (!project.getDataSource().getId().equals(cfg.getId())) {
            throw new BadRequestException("Project " + projectId + " does not belong to datasource " + dataSourceId);
        }
        jiraProjectRepository.delete(project);
    }

    /**
     * Lists all Jira projects visible to the datasource's stored token, annotated with
     * {@code alreadyAttached=true} when the project is already tracked under this datasource.
     * Result is cached 60 s to avoid hammering the Jira API on every UI interaction.
     */
    @Cacheable(value = "jira-discover-projects", key = "#dataSourceId")
    public List<DiscoveredProjectDto> discoverProjectsFromJira(Long userId, Long dataSourceId) {
        DataSourceConfig cfg = resolveOwnerJiraDs(userId, dataSourceId);
        Set<String> attached = jiraProjectRepository.findAllByDataSource(cfg).stream()
                .map(JiraProjectEntity::getProjectKey)
                .collect(Collectors.toSet());
        return listProjects(cfg).stream()
                .map(p -> new DiscoveredProjectDto(p.getKey(), p.getName(), attached.contains(p.getKey())))
                .toList();
    }

    private DataSourceConfig resolveJiraDs(Long userId, Long dataSourceId) {
        DataSourceConfig cfg = dataSourceConfigRepository.findById(dataSourceId)
                .orElseThrow(() -> new NotFoundException("DataSource not found: " + dataSourceId));
        if (cfg.getType() != DataSourceType.JIRA) {
            throw new BadRequestException("Only JIRA datasources support project management");
        }
        boolean isOwner = cfg.getUser().getId().equals(userId);
        boolean hasSubscription = userProjectRegistrationRepository.existsByUserIdAndDataSourceId(userId, dataSourceId);
        if (!isOwner && !hasSubscription) {
            throw new ForbiddenException("Access denied to datasource: " + dataSourceId);
        }
        return cfg;
    }

    private DataSourceConfig resolveOwnerJiraDs(Long userId, Long dataSourceId) {
        DataSourceConfig cfg = dataSourceConfigRepository.findById(dataSourceId)
                .orElseThrow(() -> new NotFoundException("DataSource not found: " + dataSourceId));
        if (cfg.getType() != DataSourceType.JIRA) {
            throw new BadRequestException("Only JIRA datasources support project management");
        }
        if (!cfg.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the datasource owner can manage Jira projects");
        }
        return cfg;
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
