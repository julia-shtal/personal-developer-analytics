package com.juliashtal.devanalytics.helper;

/**
 * Static helpers for normalizing API base URLs and parsing source values.
 */
public class ParsingHelper {

    public static String resolveApiBase(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()
                || configuredBaseUrl.equalsIgnoreCase("https://github.com")) {
            return "https://api.github.com";
        }
        return configuredBaseUrl.stripTrailing().replaceAll("/$", "");
    }
}
