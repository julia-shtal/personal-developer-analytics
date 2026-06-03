package com.juliashtal.devanalytics.helper;

public class ParsingHelper {

    public static String resolveApiBase(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()
                || configuredBaseUrl.equalsIgnoreCase("https://github.com")) {
            return "https://api.github.com";
        }
        return configuredBaseUrl.stripTrailing().replaceAll("/$", "");
    }

    /** Resolves a GitLab base URL (e.g. {@code https://gitlab.com}) to the API v4 root. */
    public static String resolveGitLabApiBase(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            return "https://gitlab.com/api/v4";
        }
        String stripped = configuredBaseUrl.strip().replaceAll("/$", "");
        if (stripped.equalsIgnoreCase("https://gitlab.com")) {
            return "https://gitlab.com/api/v4";
        }
        return stripped + "/api/v4";
    }
}
