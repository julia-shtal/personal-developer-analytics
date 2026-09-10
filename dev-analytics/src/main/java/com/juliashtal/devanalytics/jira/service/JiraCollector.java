package com.juliashtal.devanalytics.jira.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.JiraException;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.model.IssueSource;
import com.juliashtal.devanalytics.issue.model.JiraSearchResponse;
import com.juliashtal.devanalytics.jira.model.JiraProjectRepoMapping;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepoMappingRepository;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepository;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Collects Jira issues for tracked projects via the Jira REST API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JiraCollector {

    private final RestTemplate restTemplate;
    private final IssueRepository issueRepository;
    private final JiraProjectRepository jiraProjectRepository;
    private final JiraProjectRepoMappingRepository jiraProjectRepoMappingRepository;
    private final TokenEncryptor tokenEncryptor;
    private final AuthorIdentityService authorIdentityService;
    ObjectMapper objectMapper = new ObjectMapper();

    @Value("${jira.page-size:100}")
    private int pageSize;

    /** Jira project keys: an uppercase letter followed by uppercase alphanumerics or underscore. */
    private static final Pattern PROJECT_KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private static final DateTimeFormatter JIRA_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT);

    @Transactional
    public int collectIssues(JiraProjectEntity project) {
        // Re-fetch so the entity is managed here: the caller loaded it in another transaction,
        // and a detached @ManyToOne target throws DetachedObjectException on flush in Hibernate 6.
        project = jiraProjectRepository.getReferenceById(project.getId());
        DataSourceConfig config = project.getDataSource();
        String baseUrl = config.getBaseUrl();
        String searchUrl = baseUrl + "/rest/api/3/search/jql";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String decryptedToken = tokenEncryptor.decrypt(config.getApiTokenEncrypted());
        String[] parts = decryptedToken.split(":", 2);
        headers.setBasicAuth(parts[0], parts[1]);

        String accountId = fetchCurrentUserAccountId(baseUrl, headers);
        log.info("Jira authenticated for dataSourceId={}", config.getId());

        String jql = buildJql(project.getProjectKey());

        log.info("Starting Jira issue collection from: {}, project: {}, jql: {}",
                baseUrl, project.getProjectKey(), jql);

        int saved = fetchAndUpsertIssues(searchUrl, jql, headers, project);

        project.setLastScanAt(Instant.now());

        // Links the token owner's Jira account so they never type an accountId. A no-op when
        // they already have one, or when another user holds it.
        if (config.getUser() != null) {
            authorIdentityService.claimJiraAccountIdIfAbsent(config.getUser().getId(), accountId);
        }

        log.info("Jira collection complete: {} issues collected from {}, project: {}",
                saved, baseUrl, project.getProjectKey());
        return saved;
    }

    /**
     * Pages through the Jira search endpoint and upserts each returned issue against
     * {@code project}, advancing {@code startAt} until all results have been fetched.
     */
    private int fetchAndUpsertIssues(String searchUrl, String jql, HttpHeaders headers, JiraProjectEntity project) {
        int saved = 0;
        int startAt = 0;
        int total = Integer.MAX_VALUE;

        while (startAt < total) {
            URI uri = UriComponentsBuilder.fromUriString(searchUrl)
                    .queryParam("jql", jql)
                    .queryParam("startAt", startAt)
                    .queryParam("maxResults", pageSize)
                    .queryParam("fields", "key,summary,description,assignee,reporter,created,updated,resolutiondate,status,labels")
                    .build().toUri();

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new JiraException("Jira API error: " + response.getStatusCode());
            }

            JiraSearchResponse resp = parseResponse(response.getBody());
            total = resp.getTotal();

            List<JiraSearchResponse.JiraIssue> issues = resp.getIssues();
            if (issues == null || issues.isEmpty()) break;

            for (JiraSearchResponse.JiraIssue ji : issues) {
                upsertJiraIssue(project, ji);
                saved++;
            }

            startAt += issues.size();
            log.debug("Jira page fetched: saved={}, startAt={}, total={}", saved, startAt, total);
        }

        return saved;
    }

    private String fetchCurrentUserAccountId(String baseUrl, HttpHeaders headers) {
        URI uri = URI.create(baseUrl + "/rest/api/3/myself");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new JiraException("Failed to fetch Jira current user, status: "
                        + response.getStatusCode());
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode accountIdNode = root.get("accountId");

            if (accountIdNode == null || accountIdNode.isNull()) {
                throw new JiraException("Jira /myself response missing accountId field. " +
                        "Response: " + response.getBody());
            }

            return accountIdNode.asText();

        } catch (JiraException e) {
            throw e;
        } catch (Exception e) {
            throw new JiraException("Error fetching Jira current user from: " + uri, e);
        }
    }

    /**
     * JQL for the whole project, with no assignee clause: attribution happens in the metric
     * queries against each issue's stored accountId, so collection must return every issue.
     *
     * <p>The key is validated rather than escaped because it is interpolated into JQL; quoting it
     * also keeps reserved words from parsing as operators.</p>
     */
    // Package-private so JiraCollectorJqlTest can assert the clause without a live Jira.
    String buildJql(String projectKey) {
        if (projectKey == null || !PROJECT_KEY_PATTERN.matcher(projectKey).matches()) {
            throw new IllegalStateException("Invalid Jira project key: " + projectKey);
        }
        return "project = \"" + projectKey + "\" ORDER BY created DESC";
    }

    private JiraSearchResponse parseResponse(String body) {
        try {
            return objectMapper.readValue(body, JiraSearchResponse.class);
        } catch (JsonProcessingException e) {
            throw new JiraException("Failed to parse Jira response", e);
        }
    }

    // Package-private for JiraIssueUpsertTest; the only other route here is a live Jira.
    void upsertJiraIssue(JiraProjectEntity project, JiraSearchResponse.JiraIssue jiraIssue) {
        String sourceIssueKey = jiraIssue.getKey();

        IssueEntity issue = issueRepository
                .findByJiraProjectAndSourceIssueKey(project, sourceIssueKey)
                .orElseGet(IssueEntity::new);

        issue.setJiraProject(project);
        issue.setSource(IssueSource.JIRA);
        issue.setSourceContext(project.getProjectKey());
        issue.setSourceIssueKey(sourceIssueKey);

        JiraSearchResponse.Fields f = jiraIssue.getFields();
        if (f != null) {
            issue.setTitle(f.getSummary());
            issue.setDescription(extractPlainText(f.getDescription()));

            issue.setAssignee(f.getAssignee() != null ? f.getAssignee().getDisplayName() : null);
            issue.setCreator(f.getReporter() != null ? f.getReporter().getDisplayName() : null);
            // Display names above are kept for the UI; these are what the metric queries match.
            issue.setAssigneeAccountId(f.getAssignee() != null ? f.getAssignee().getAccountId() : null);
            issue.setReporterAccountId(f.getReporter() != null ? f.getReporter().getAccountId() : null);

            issue.setCreatedAt(parseJiraDate(f.getCreated()));
            issue.setUpdatedAt(parseJiraDate(f.getUpdated()));
            issue.setClosedAt(parseJiraDate(f.getResolutiondate()));

            issue.setState(f.getStatus() != null ? f.getStatus().getName() : null);

            if (f.getLabels() != null) {
                issue.setLabels(String.join(",", f.getLabels()));
            }
        }

        // Attribute the issue to the first mapped GitHub repo, if one is configured.
        List<JiraProjectRepoMapping> mappings = jiraProjectRepoMappingRepository.findAllByJiraProject(project);
        if (!mappings.isEmpty()) {
            issue.setRepository(mappings.get(0).getRepository());
        } else {
            log.debug("No GitHub repo mapped to Jira project {}, issue {} will have no repository attribution",
                    project.getProjectKey(), sourceIssueKey);
        }

        issueRepository.save(issue);
    }

    private String extractPlainText(Object descriptionObj) {
        if (descriptionObj == null)
            return null;

        try {
            if (descriptionObj instanceof String) {
                return (String) descriptionObj;
            }

            JsonNode node = objectMapper.valueToTree(descriptionObj);
            return extractTextFromADF(node);
        } catch (Exception e) {
            log.warn("Failed to parse description ADF, using raw: {}", descriptionObj, e);
            return descriptionObj.toString();
        }
    }

    private String extractTextFromADF(JsonNode node) {
        if (node == null || node.isNull())
            return "";

        StringBuilder text = new StringBuilder();

        if (node.has("content") && node.get("content").isArray()) {
            for (JsonNode child : node.get("content")) {
                text.append(extractTextFromADF(child));
            }
        } else if (node.has("text")) {
            text.append(node.get("text").asText());
        } else if (node.isTextual()) {
            text.append(node.asText());
        }

        // Add newlines for blocks (paragraph, list, table)
        if (node.has("type") && List.of("paragraph", "listItem", "tableRow").contains(node.get("type").asText())) {
            text.append("\n");
        }

        return text.toString().trim();
    }

    private Instant parseJiraDate(String date) {
        if (date == null || date.isBlank())
            return null;
        return ZonedDateTime.parse(date, JIRA_DATE_TIME).toInstant();
    }
}
