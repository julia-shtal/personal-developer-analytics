package com.juliashtal.devanalytics.ai.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("ollama")
@Slf4j
public class OllamaHealthIndicator implements HealthIndicator {

    private final String baseUrl;
    private final RestTemplate restTemplate;

    public OllamaHealthIndicator(
            @Value("${ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(3_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public Health health() {
        try {
            // Ollama's root endpoint returns a plain "Ollama is running" response
            restTemplate.getForObject(baseUrl, String.class);
            return Health.up().withDetail("url", baseUrl).build();
        } catch (Exception e) {
            log.debug("Ollama health check failed: {}", e.getMessage());
            return Health.down().withDetail("url", baseUrl).withException(e).build();
        }
    }
}
