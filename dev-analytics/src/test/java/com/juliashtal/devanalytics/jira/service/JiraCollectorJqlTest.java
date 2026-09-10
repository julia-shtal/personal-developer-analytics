package com.juliashtal.devanalytics.jira.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.issue.model.JiraSearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance tests for Jira collection scope and identity mapping.
 *
 * <p>The JQL carries no assignee clause: attribution happens in the metric queries, which only
 * works if collection returns the whole project. The key is interpolated rather than bound, so
 * validation is what keeps it safe, and quoting stops a reserved word parsing as an operator.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraCollectorJqlTest {

    @InjectMocks JiraCollector collector;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildJql_validProjectKey_scopesToTheWholeProjectWithoutAnAssigneeClause() {
        String jql = collector.buildJql("PDA");

        assertThat(jql).isEqualTo("project = \"PDA\" ORDER BY created DESC");
        // Any assignee clause would make the stored rows depend on whose token collected them.
        assertThat(jql).doesNotContain("assignee");
    }

    @Test
    void buildJql_keyWithDigitsAndUnderscore_isAccepted() {
        assertThat(collector.buildJql("PDA_2")).isEqualTo("project = \"PDA_2\" ORDER BY created DESC");
    }

    @Test
    void buildJql_nullKey_throwsIllegalState() {
        assertThatThrownBy(() -> collector.buildJql(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildJql_blankKey_throwsIllegalState() {
        // A missing key must not silently produce an unscoped query across every project.
        assertThatThrownBy(() -> collector.buildJql("  "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildJql_lowercaseKey_throwsIllegalState() {
        assertThatThrownBy(() -> collector.buildJql("pda"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildJql_keyContainingQuoteAndClause_throwsIllegalState() {
        // The injection shape: without validation this would close the quote and append a clause.
        assertThatThrownBy(() -> collector.buildJql("PDA\" OR project = \"OTHER"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jiraUser_searchResponse_deserializesAccountId() throws Exception {
        // accountId is what issue attribution matches on; display names are not unique or stable.
        String body = """
            {"total": 1, "issues": [{"id": "1", "key": "PDA-1", "fields": {
               "summary": "An issue",
               "assignee": {"accountId": "5b10a2844c20165700ede21g", "displayName": "Alice"},
               "reporter": {"accountId": "5b10ac8d82e05b22cc7d4ef5", "displayName": "Bob"}
            }}]}
            """;

        JiraSearchResponse response = objectMapper.readValue(body, JiraSearchResponse.class);
        JiraSearchResponse.Fields fields = response.getIssues().get(0).getFields();

        assertThat(fields.getAssignee().getAccountId()).isEqualTo("5b10a2844c20165700ede21g");
        assertThat(fields.getReporter().getAccountId()).isEqualTo("5b10ac8d82e05b22cc7d4ef5");
        assertThat(fields.getAssignee().getDisplayName()).isEqualTo("Alice");
    }
}
