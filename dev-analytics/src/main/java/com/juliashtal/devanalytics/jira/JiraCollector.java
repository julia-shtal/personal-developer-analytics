package com.juliashtal.devanalytics.jira;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.JiraException;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.JiraSearchResponse;
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
    private final SimpleTokenEncryptor tokenEncryptor;
    ObjectMapper objectMapper = new ObjectMapper();

    @Value("${jira.default-jql:assignee = currentUser() ORDER BY created DESC}")
    private String defaultJql;

    @Value("${jira.page-size:100}")
    private int pageSize;

    private static final DateTimeFormatter JIRA_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT);

    @Transactional
    public int collectIssues(DataSourceConfig config) {
        String jql = defaultJql;
        if (jql == null || jql.trim().isEmpty()) {
            throw new IllegalArgumentException("JQL required.");
        }

        String baseUrl = config.getBaseUrl();
        String searchUrl = baseUrl + "/rest/api/3/search/jql";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String decryptedToken = tokenEncryptor.decrypt(config.getApiTokenEncrypted());
        String[] parts = decryptedToken.split(":", 2);
        headers.setBasicAuth(parts[0], parts[1]);

        int saved = 0;
        int startAt = 0;
        List<JiraSearchResponse.JiraIssue> issues;

        do {
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
            issues = resp.getIssues();

            if (issues.isEmpty())
                break;

            for (JiraSearchResponse.JiraIssue ji : issues) {
                upsertJiraIssue(config, ji);
                saved++;
            }

            startAt += pageSize;

            log.info("Fetched {} issues (page startAt={}, pageSize={})", saved, startAt - pageSize, issues.size());

        } while (issues.size() == pageSize);

        return saved;
    }

    private JiraSearchResponse parseResponse(String body) {
        try {
            return objectMapper.readValue(body, JiraSearchResponse.class);
        } catch (JsonProcessingException e) {
            throw new JiraException("Failed to parse Jira response", e);
        }
    }

    private void upsertJiraIssue(DataSourceConfig config, JiraSearchResponse.JiraIssue jiraIssue) {
        String externalId = jiraIssue.getKey();

        IssueEntity issue = issueRepository
                .findByDataSourceAndExternalId(config, externalId)
                .orElseGet(IssueEntity::new);

        issue.setDataSource(config);
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

