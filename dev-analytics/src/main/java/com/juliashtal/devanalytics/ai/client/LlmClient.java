package com.juliashtal.devanalytics.ai.client;

/**
 * Abstraction over a large-language-model completion backend.
 */
public interface LlmClient {
    String complete(String model, String systemPrompt, String userPrompt, boolean jsonMode);

    /** The runtime's reported version, or {@code "UNKNOWN"} if it cannot be read. */
    String runtimeVersion();
}
