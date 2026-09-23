package com.juliashtal.devanalytics.ai.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * LlmClient backed by a local Ollama server over its HTTP API.
 */
@Service
@Slf4j
public class OllamaLlmClient implements LlmClient {

    private final String baseUrl;
    private final int numPredict;
    private final RestTemplate restTemplate;

    private final int seed;
    private final int numThread;
    private final int topK;
    private final double topP;
    private final double repeatPenalty;
    private final int numCtx;

    public OllamaLlmClient(
            @Value("${ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ai.ollama.num-predict:1024}") int numPredict,
            @Value("${ai.ollama.seed:42}") int seed,
            @Value("${ai.ollama.num-thread:1}") int numThread,
            @Value("${ai.ollama.top-k:1}") int topK,
            @Value("${ai.ollama.top-p:0.0}") double topP,
            @Value("${ai.ollama.repeat-penalty:1.0}") double repeatPenalty,
            @Value("${ai.ollama.num-ctx:8192}") int numCtx) {
        this.baseUrl = baseUrl;
        this.numPredict = numPredict;
        this.seed = seed;
        this.numThread = numThread;
        this.topK = topK;
        this.topP = topP;
        this.repeatPenalty = repeatPenalty;
        this.numCtx = numCtx;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(300_000); // 5 minutes — LLM inference can be slow
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String complete(String model, String systemPrompt, String userPrompt, boolean jsonMode) {
        OllamaRequest req = new OllamaRequest();
        req.setModel(model);
        req.setSystem(systemPrompt);
        req.setPrompt(userPrompt);
        req.setStream(false);
        if (jsonMode) req.setFormat("json");
        req.setOptions(Map.of(
                "num_predict", numPredict,
                "temperature", 0.0,
                "seed", seed,
                "num_thread", numThread,
                "top_k", topK,
                "top_p", topP,
                "repeat_penalty", repeatPenalty,
                "num_ctx", numCtx));

        log.debug("Sending request to Ollama: model={}, promptLength={}, numPredict={}, seed={}, numThread={}, topK={}, topP={}, repeatPenalty={}, numCtx={}",
                model, userPrompt.length(), numPredict, seed, numThread, topK, topP, repeatPenalty, numCtx);
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class OllamaRequest {
        private String model;
        private String system;
        private String prompt;
        private boolean stream;
        private String format;
        private Map<String, Object> options;
    }

    @Data
    static class OllamaResponse {
        private String model;
        private String response;
    }
}
