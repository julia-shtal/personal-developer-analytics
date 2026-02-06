package com.juliashtal.devanalytics.issue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
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
        //private String description;
        private JiraUser assignee;
        private JiraUser reporter;
        private String created;
        private String updated;
        private String resolutiondate;
        private JiraStatus status;     // ← single object, not list
        private List<String> labels;   // ← list of strings, not JiraLabel
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraUser {
        private String displayName;
        private String emailAddress;
        // optional: private String accountId; String self; if needed
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraStatus {
        private String name;
        // optional: private String id; String self; etc.
    }

    // Drop JiraLabel; labels is just List<String>
}
