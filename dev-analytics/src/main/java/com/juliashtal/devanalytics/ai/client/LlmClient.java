package com.juliashtal.devanalytics.ai.client;

public interface LlmClient {
    String complete(String model, String systemPrompt, String userPrompt);
}
