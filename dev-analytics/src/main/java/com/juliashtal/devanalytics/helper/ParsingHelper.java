package com.juliashtal.devanalytics.helper;

public class ParsingHelper {

    public static String resolveApiBase(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()
                || configuredBaseUrl.equalsIgnoreCase("https://github.com")) {
            return "https://api.github.com";
        }
        return configuredBaseUrl.stripTrailing().replaceAll("/$", "");
    }
}
