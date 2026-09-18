package com.juliashtal.devanalytics.jira.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepoMappingRepository;
import com.juliashtal.devanalytics.jira.repository.JiraProjectRepository;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that a Jira collection reads every page.
 *
 * <p>{@code /rest/api/3/search/jql} replaced {@code /rest/api/3/search} (CHANGE-2046) and
 * returns neither a total nor an offset: a caller follows {@code nextPageToken} until
 * {@code isLast}. Paging toward a count stops after one page, because an absent {@code total}
 * deserialises to zero — a truncated collection that reports success.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraCollectorPagingTest {

    @Mock RestTemplate restTemplate;
    @Mock IssueRepository issueRepository;
    @Mock JiraProjectRepository jiraProjectRepository;
    @Mock JiraProjectRepoMappingRepository jiraProjectRepoMappingRepository;
    @Mock TokenEncryptor tokenEncryptor;
    @Mock AuthorIdentityService authorIdentityService;

    @InjectMocks JiraCollector collector;

    private static final String BASE = "https://tracker.example.test";
    private static final URI MYSELF = URI.create(BASE + "/rest/api/3/myself");
    private static final ArgumentMatcher<URI> SEARCH =
            uri -> uri != null && uri.getPath().contains("/search/jql");

    private JiraProjectEntity project;

    @BeforeEach
    void setUp() {
        DataSourceConfig config = new DataSourceConfig();
        config.setId(3L);
        config.setType(DataSourceType.JIRA);
        config.setBaseUrl(BASE);
        config.setApiTokenEncrypted("encrypted");

        project = new JiraProjectEntity();
        project.setId(5L);
        project.setProjectKey("HV");
        project.setDataSource(config);

        ReflectionTestUtils.setField(collector, "pageSize", 2);
        when(jiraProjectRepository.getReferenceById(5L)).thenReturn(project);
        when(tokenEncryptor.decrypt("encrypted")).thenReturn("user@example.test:token");
        when(issueRepository.findByJiraProjectAndSourceIssueKey(any(), any()))
                .thenReturn(Optional.empty());
        when(jiraProjectRepoMappingRepository.findAllByJiraProject(any())).thenReturn(List.of());
        // The account lookup runs after the search and is not what these tests are about.
        when(restTemplate.exchange(eq(MYSELF), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void collectIssues_multiplePages_followsTheTokenUntilIsLast() {
        givenPages(page(List.of("HV-1", "HV-2"), "tok-2", false),
                   page(List.of("HV-3", "HV-4"), "tok-3", false),
                   page(List.of("HV-5"), null, true));

        assertThat(collector.collectIssues(project)).isEqualTo(5);

        ArgumentCaptor<URI> uris = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, times(3))
                .exchange(uris.capture(), eq(HttpMethod.GET), any(), eq(String.class));
        List<URI> searches = uris.getAllValues().stream().filter(SEARCH::matches).toList();

        assertThat(searches).hasSize(3);
        assertThat(searches.get(0).getQuery()).doesNotContain("nextPageToken");
        assertThat(searches.get(1).getQuery()).contains("nextPageToken=tok-2");
        assertThat(searches.get(2).getQuery()).contains("nextPageToken=tok-3");
    }

    @Test
    void collectIssues_responseWithoutTotal_doesNotStopAfterTheFirstPage() {
        // The regression this guards: no `total` field at all, which used to read as zero.
        givenPages("{\"issues\":[{\"key\":\"HV-1\",\"fields\":{\"summary\":\"s\"}}],"
                        + "\"nextPageToken\":\"tok-2\",\"isLast\":false}",
                   "{\"issues\":[{\"key\":\"HV-2\",\"fields\":{\"summary\":\"s\"}}],"
                        + "\"isLast\":true}");

        assertThat(collector.collectIssues(project)).isEqualTo(2);
    }

    @Test
    void collectIssues_lastPage_makesNoFurtherRequest() {
        givenPages(page(List.of("HV-1"), null, true));

        assertThat(collector.collectIssues(project)).isEqualTo(1);
        verify(restTemplate, times(1))
                .exchange(argThat(SEARCH), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void collectIssues_noTokenAndNotFlaggedLast_stopsRatherThanRepeatingThePage() {
        givenPages(page(List.of("HV-1"), null, false));

        assertThat(collector.collectIssues(project)).isEqualTo(1);
        verify(restTemplate, times(1))
                .exchange(argThat(SEARCH), eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void collectIssues_repeatedToken_stopsInsteadOfLooping() {
        // A server that keeps handing back the same token would otherwise page for ever.
        givenPages(page(List.of("HV-1"), "same", false),
                   page(List.of("HV-2"), "same", false));

        assertThat(collector.collectIssues(project)).isEqualTo(2);
        verify(restTemplate, times(2))
                .exchange(argThat(SEARCH), eq(HttpMethod.GET), any(), eq(String.class));
    }

    // -------------------------------------------------------------------------

    private void givenPages(String first, String... rest) {
        var stub = when(restTemplate.exchange(argThat(SEARCH), eq(HttpMethod.GET), any(),
                eq(String.class))).thenReturn(ResponseEntity.ok(first));
        for (String body : rest) {
            stub = stub.thenReturn(ResponseEntity.ok(body));
        }
    }

    /** One page of the token-paged search response. */
    private String page(List<String> keys, String nextPageToken, boolean isLast) {
        String issues = keys.stream()
                .map(k -> "{\"key\":\"" + k + "\",\"fields\":{\"summary\":\"an issue\"}}")
                .reduce((a, b) -> a + "," + b).orElse("");
        String token = nextPageToken == null ? "" : ",\"nextPageToken\":\"" + nextPageToken + "\"";
        return "{\"issues\":[" + issues + "]" + token + ",\"isLast\":" + isLast + "}";
    }
}
