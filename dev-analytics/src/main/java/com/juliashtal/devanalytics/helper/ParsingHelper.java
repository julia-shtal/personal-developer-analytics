package com.juliashtal.devanalytics.helper;

/**
 * Static helpers for normalizing API base URLs and parsing source values.
 */
//TODO check do we need this helper? or do we have the same code samples in the whole project?
public class ParsingHelper {

    public static String resolveApiBase(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()
                || configuredBaseUrl.equalsIgnoreCase("https://github.com")) {
            return "https://api.github.com";
        }
        return configuredBaseUrl.stripTrailing().replaceAll("/$", "");
    }
}
