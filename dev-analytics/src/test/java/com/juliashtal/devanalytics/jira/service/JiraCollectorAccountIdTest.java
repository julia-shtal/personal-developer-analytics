package com.juliashtal.devanalytics.jira.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepoMappingRepository;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepository;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that resolving the credential owner's Jira account cannot prevent issues from being
 * collected.
 *
 * <p>The accountId spares the user from typing their own identifier and is a no-op when they
 * already have one, so it is a convenience. A Jira site that serves its issues to anyone but
 * answers {@code /myself} only to its own members is a real configuration — an open-source
 * project's tracker is exactly that — and on one of those the issues must still collect.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraCollectorAccountIdTest {

    @Mock RestTemplate restTemplate;
    @Mock IssueRepository issueRepository;
    @Mock JiraProjectRepository jiraProjectRepository;
    @Mock JiraProjectRepoMappingRepository jiraProjectRepoMappingRepository;
    @Mock TokenEncryptor tokenEncryptor;
    @Mock AuthorIdentityService authorIdentityService;

    @InjectMocks JiraCollector collector;

    private static final String BASE = "https://tracker.example.test";
    private JiraProjectEntity project;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(7L);

        DataSourceConfig config = new DataSourceConfig();
        config.setId(3L);
        config.setType(DataSourceType.JIRA);
        config.setBaseUrl(BASE);
        config.setApiTokenEncrypted("encrypted");
        config.setUser(owner);

        project = new JiraProjectEntity();
        project.setId(5L);
        project.setProjectKey("HV");
        project.setDataSource(config);

        when(jiraProjectRepository.getReferenceById(5L)).thenReturn(project);
        when(tokenEncryptor.decrypt("encrypted")).thenReturn("user@example.test:token");
        when(issueRepository.findByJiraProjectAndSourceIssueKey(any(), any()))
                .thenReturn(Optional.empty());
        when(jiraProjectRepoMappingRepository.findAllByJiraProject(any())).thenReturn(List.of());

        // The search and /myself share the same exchange(..., String.class) overload, so the
        // two are told apart by URI rather than by response type.
        when(restTemplate.exchange(argThat(SEARCH), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(ONE_ISSUE_PAGE));
    }

    /** Matches the JQL search, whose URI carries query parameters. */
    private static final ArgumentMatcher<URI> SEARCH =
            uri -> uri != null && uri.getPath().contains("/search/jql");

    private static final URI MYSELF = URI.create(BASE + "/rest/api/3/myself");

    private static final String ONE_ISSUE_PAGE = """
            {"total":1,"isLast":true,"issues":[
              {"key":"HV-1","fields":{"summary":"an issue",
               "created":"2026-03-01T10:00:00.000+0000"}}]}
            """;

    @Test
    void collectIssues_myselfUnauthorised_stillCollectsAndSkipsTheAccountClaim() {
        when(restTemplate.exchange(eq(MYSELF), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        int saved = collector.collectIssues(project);

        assertThat(saved).isEqualTo(1);
        verify(authorIdentityService, never()).claimJiraAccountIdIfAbsent(any(), any());
    }

    @Test
    void collectIssues_myselfAvailable_collectsAndClaimsTheAccountId() {
        when(restTemplate.exchange(eq(MYSELF), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"accountId\":\"acct-owner\"}"));

        int saved = collector.collectIssues(project);

        assertThat(saved).isEqualTo(1);
        verify(authorIdentityService).claimJiraAccountIdIfAbsent(7L, "acct-owner");
    }

    @Test
    void collectIssues_myselfFails_doesNotAbortBeforeTheSearch() {
        // The search runs first now, so a failing /myself cannot stop it: the regression this
        // guards against collected zero issues from a publicly readable project.
        when(restTemplate.exchange(eq(MYSELF), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        collector.collectIssues(project);

        verify(restTemplate).exchange(argThat(SEARCH), eq(HttpMethod.GET), any(), eq(String.class));
        verify(issueRepository).save(any());
    }

}
