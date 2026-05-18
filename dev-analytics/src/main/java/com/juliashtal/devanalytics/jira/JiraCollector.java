package com.juliashtal.devanalytics.jira;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.JiraException;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.JiraSearchResponse;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraCollector {

    private final RestTemplate restTemplate;
    private final IssueRepository issueRepository;
    private final JiraProjectRepository jiraProjectRepository;
    private final SimpleTokenEncryptor tokenEncryptor;
    ObjectMapper objectMapper = new ObjectMapper();

    @Value("${jira.page-size:100}")
    private int pageSize;

    private static final DateTimeFormatter JIRA_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT);

    @Transactional
    public int collectIssues(JiraProjectEntity project) {
        // Re-fetch so the entity is managed in this session's persistence context.
        // The caller loaded `project` in a separate transaction; using a detached entity
        // as a @ManyToOne target causes a DetachedObjectException on flush in Hibernate 6.
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
        log.info("Jira authenticated as accountId={}", accountId);

        String jql = buildJql(project.getProjectKey(), accountId);

        int saved = 0;
        int startAt = 0;
        int total = Integer.MAX_VALUE;

        log.info("Starting Jira issue collection from: {}, project: {}, jql: {}",
                baseUrl, project.getProjectKey(), jql);
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

        project.setLastScanAt(Instant.now());
        log.info("Jira collection complete: {} issues collected from {}, project: {}",
                saved, baseUrl, project.getProjectKey());
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

    private String buildJql(String projectKey, String accountId) {
        String assigneeFilter = "assignee = " + accountId + " ";
        if (projectKey != null && !projectKey.isBlank()) {
            return "project = " + projectKey + " AND "
                    + assigneeFilter + " ORDER BY created DESC";
        }
        return assigneeFilter + " ORDER BY created DESC";
    }

    private JiraSearchResponse parseResponse(String body) {
        try {
            return objectMapper.readValue(body, JiraSearchResponse.class);
        } catch (JsonProcessingException e) {
            throw new JiraException("Failed to parse Jira response", e);
        }
    }

    private void upsertJiraIssue(JiraProjectEntity project, JiraSearchResponse.JiraIssue jiraIssue) {
        String externalId = jiraIssue.getKey();

        IssueEntity issue = issueRepository
                .findByJiraProjectAndExternalId(project, externalId)
                .orElseGet(IssueEntity::new);

        issue.setJiraProject(project);
        issue.setExternalId(externalId);

        JiraSearchResponse.Fields f = jiraIssue.getFields();
        if (f != null) {
            issue.setTitle(f.getSummary());
            issue.setDescription(extractPlainText(f.getDescription()));

            issue.setAssignee(f.getAssignee() != null ? f.getAssignee().getDisplayName() : null);
            issue.setCreator(f.getReporter() != null ? f.getReporter().getDisplayName() : null);

            issue.setCreatedAt(parseJiraDate(f.getCreated()));
            issue.setUpdatedAt(parseJiraDate(f.getUpdated()));
            issue.setClosedAt(parseJiraDate(f.getResolutiondate()));

            issue.setState(f.getStatus() != null ? f.getStatus().getName() : null);

            if (f.getLabels() != null) {
                issue.setLabels(String.join(",", f.getLabels()));
            }
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
