package com.juliashtal.devanalytics.jira;

/** Normalization helpers for Jira instance base URLs. */
public final class JiraUrl {

    private JiraUrl() {}

    /**
     * Returns a stable comparison key for a Jira base URL: lowercased, leading/trailing
     * whitespace stripped, trailing slashes removed. Stored in {@code jira_projects.base_url_normalized}.
     */
    public static String normalize(String baseUrl) {
        if (baseUrl == null) return null;
        return baseUrl.trim().toLowerCase().replaceAll("/+$", "");
    }
}
