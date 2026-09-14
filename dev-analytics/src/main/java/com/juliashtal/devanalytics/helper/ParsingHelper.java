package com.juliashtal.devanalytics.helper;

/**
 * Static helpers for normalizing GitHub API base URLs.
 */
public final class ParsingHelper {

    private ParsingHelper() {
    }

    /**
     * Resolves a configured base URL to the API host to call.
     * <p>An absent URL or any github.com web URL maps to the public API host; a GitHub Enterprise
     * URL is returned trimmed of trailing slashes.</p>
     */
    public static String resolveApiBase(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            return "https://api.github.com";
        }
        String trimmed = configuredBaseUrl.strip().replaceAll("/+$", "");
        // "https://github.com" must match the host exactly or up to a path separator,
        // otherwise a GHES host such as https://github.company.com is misrouted to the public API.
        if (trimmed.equalsIgnoreCase("https://github.com")
                || trimmed.regionMatches(true, 0, "https://github.com/", 0, "https://github.com/".length())) {
            return "https://api.github.com";
        }
        return trimmed;
    }
}
