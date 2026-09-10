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
    private int total;
    private List<JiraIssue> issues;
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
