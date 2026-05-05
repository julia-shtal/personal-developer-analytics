package com.juliashtal.devanalytics.ai.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class OllamaLlmClient implements LlmClient {

    private final String baseUrl;
    private final RestTemplate restTemplate;

    public OllamaLlmClient(
            @Value("${ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(300_000); // 5 minutes — LLM inference can be slow
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String complete(String model, String systemPrompt, String userPrompt) {
        OllamaRequest req = new OllamaRequest(model, systemPrompt, userPrompt, false);
        log.debug("Sending request to Ollama: model={}, promptLength={}", model, userPrompt.length());
        try {
            OllamaResponse resp = restTemplate.postForObject(
                    baseUrl + "/api/generate", req, OllamaResponse.class);
            if (resp == null || resp.getResponse() == null || resp.getResponse().isBlank()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Empty response from Ollama model '" + model + "'");
            }
            log.debug("Ollama response received: responseLength={}", resp.getResponse().length());
            return resp.getResponse();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ollama request failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI model is unavailable. Ensure Ollama is running at " + baseUrl + ". Error: " + e.getMessage());
        }
    }

    @Data
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class OllamaRequest {
        private String model;
        private String system;
        private String prompt;
        private boolean stream;
    }

    @Data
    static class OllamaResponse {
        private String model;
        private String response;
    }
}
