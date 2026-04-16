package com.juliashtal.devanalytics.issue.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraSearchResponse {
    private List<JiraIssue> issues;
    private Boolean isLast;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraIssue {
        private String id;
        private String key;
        private Fields fields;
    }

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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraUser {
        private String displayName;
        private String emailAddress;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraStatus {
        private String name;
    }
}
