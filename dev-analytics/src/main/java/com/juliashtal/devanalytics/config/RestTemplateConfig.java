package com.juliashtal.devanalytics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Provides the shared RestTemplate used for outbound HTTP calls.
 *
 * <p>Timeouts bound each hop because the default factory waits forever, and
 * {@code JiraProjectService.discoverProjectsFromJira} runs on the request thread — an unreachable
 * Jira host would otherwise hold a Tomcat worker until the client gave up. They bound a single
 * request, not a paged loop over many.</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${app.http.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${app.http.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

}
