package com.juliashtal.devanalytics.issue.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * Deserialized Jira issue-search API response.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraSearchResponse {
    private List<JiraIssue> issues;

    /**
     * Token-based paging, which is what {@code /rest/api/3/search/jql} provides. The endpoint
     * that replaced {@code /rest/api/3/search} (CHANGE-2046) returns neither {@code total} nor
     * an offset, so there is no count to page toward: a caller follows {@code nextPageToken}
     * until {@code isLast}. No {@code total} field is modelled here on purpose — an absent one
     * deserialises to zero, which reads as "no more pages" and silently truncates a collection.
     */
    private String nextPageToken;
    private Boolean isLast;

    /**
     * A single issue in a Jira search response.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraIssue {
        private String id;
        private String key;
        private Fields fields;
    }

    /**
     * Issue fields returned by the Jira search API.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {
        private String summary;
        private Object description;
        private JiraUser assignee;
        private JiraUser reporter;
        private String created;
        private String updated;
        private String resolutiondate;
        private JiraStatus status;
        private List<String> labels;
    }

    /**
     * A Jira user. {@code accountId} is the stable identifier attribution matches on;
     * {@code displayName} is kept for display only.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraUser {
        private String accountId;
        private String displayName;
        private String emailAddress;
    }

    /**
     * A Jira issue status.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraStatus {
        private String name;
    }
}
