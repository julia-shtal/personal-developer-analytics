package com.juliashtal.devanalytics.jira;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.JiraSearchResponse;
import com.juliashtal.devanalytics.security.SimpleTokenEncryptor;
import lombok.RequiredArgsConstructor;
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
public class JiraCollector {

    private final RestTemplate restTemplate;
    private final IssueRepository issueRepository;
    private final SimpleTokenEncryptor tokenEncryptor;
    ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public int collectIssues(DataSourceConfig config) throws JsonProcessingException {
        String jql = "assignee = currentUser() ORDER BY created DESC";

        String baseUrl = config.getBaseUrl();
        String searchUrl = baseUrl + "/rest/api/3/search/jql";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String decryptedToken = tokenEncryptor.decrypt(config.getApiTokenEncrypted());
        String[] parts = decryptedToken.split(":", 2);
        headers.setBasicAuth(parts[0], parts[1]);

        URI uri = UriComponentsBuilder.fromUriString(searchUrl)
                .queryParam("jql", jql)
                .queryParam("startAt", 0)
                .queryParam("maxResults", 200)
                .queryParam("fields", "summary,description,assignee,reporter,created,updated,resolutiondate,status,labels")
                .build()
                .toUri();

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response =
                restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

        JiraSearchResponse jiraSearchResponse =
                objectMapper.readValue(response.getBody(), JiraSearchResponse.class);

        int saved = 0;
        for (JiraSearchResponse.JiraIssue ji : jiraSearchResponse.getIssues()) {
            upsertJiraIssue(config, ji);
            saved++;
        }
        return saved;
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
            //TODO
            //issue.setDescription(f.getDescription());

            issue.setAssignee(f.getAssignee() != null ? f.getAssignee().getDisplayName() : null);
            issue.setCreator(f.getReporter() != null ? f.getReporter().getDisplayName() : null);

            issue.setCreatedAt(parseJiraDate(f.getCreated()));
            issue.setUpdatedAt(parseJiraDate(f.getUpdated()));
            issue.setClosedAt(parseJiraDate(f.getResolutiondate()));

            issue.setState(f.getStatus().getName());

            if (f.getLabels() != null) {
                issue.setLabels(String.join(",", f.getLabels()));
            }
        }

        issueRepository.save(issue);
    }

    private static final DateTimeFormatter JIRA_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT);

    private Instant parseJiraDate(String date) {
        if (date == null || date.isBlank()) return null;
        return ZonedDateTime.parse(date, JIRA_DATE_TIME).toInstant();
    }


}

