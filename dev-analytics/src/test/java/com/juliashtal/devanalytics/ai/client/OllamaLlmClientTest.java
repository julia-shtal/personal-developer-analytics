package com.juliashtal.devanalytics.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * The summarization path's only HTTP boundary: what is sent to Ollama, and how a failure or an
 * unusable reply is turned into a {@link ResponseStatusException} the caller can show the user.
 */
@WireMockTest
class OllamaLlmClientTest {

    @Test
    void complete_success_returnsTheResponseText(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(okJson("{\"model\":\"llama3.2\",\"response\":\"hello there\"}")));

        OllamaLlmClient client = client(wm);
        String result = client.complete("llama3.2", "system", "user prompt", false);

        assertThat(result).isEqualTo("hello there");
    }

    @Test
    void complete_requestBody_carriesModelPromptsAndOptions(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(okJson("{\"model\":\"llama3.2\",\"response\":\"ok\"}")));

        client(wm, 256, 7).complete("llama3.2", "be terse", "summarize this", false);

        JsonNode body = sentBody();
        assertThat(body.get("model").asText()).isEqualTo("llama3.2");
        assertThat(body.get("system").asText()).isEqualTo("be terse");
        assertThat(body.get("prompt").asText()).isEqualTo("summarize this");
        assertThat(body.get("stream").asBoolean()).isFalse();
        assertThat(body.get("options").get("num_predict").asInt()).isEqualTo(256);
        assertThat(body.get("options").get("temperature").asDouble()).isEqualTo(0.0);
        assertThat(body.get("options").get("seed").asInt()).isEqualTo(7);
    }

    @Test
    void complete_requestBody_carriesTheKeepAliveProperty(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(okJson("{\"model\":\"llama3.2\",\"response\":\"ok\"}")));

        new OllamaLlmClient(wm.getHttpBaseUrl(), 1024, 42, "0")
                .complete("llama3.2", "system", "prompt", false);

        assertThat(sentBody().get("keep_alive").asText()).isEqualTo("0");
    }

    @Test
    void complete_jsonModeTrue_setsFormatJson(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(okJson("{\"model\":\"llama3.2\",\"response\":\"{}\"}")));

        client(wm).complete("llama3.2", "system", "prompt", true);

        assertThat(sentBody().get("format").asText()).isEqualTo("json");
    }

    @Test
    void complete_jsonModeFalse_omitsFormatField(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(okJson("{\"model\":\"llama3.2\",\"response\":\"plain text\"}")));

        client(wm).complete("llama3.2", "system", "prompt", false);

        assertThat(sentBody().has("format")).isFalse();
    }

    @Test
    void complete_blankResponseField_throwsServiceUnavailable(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(okJson("{\"model\":\"llama3.2\",\"response\":\"\"}")));

        assertThatThrownBy(() -> client(wm).complete("llama3.2", "system", "prompt", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Empty response")
                .hasMessageContaining("llama3.2")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(SERVICE_UNAVAILABLE);
    }

    @Test
    void complete_missingResponseField_throwsServiceUnavailable(WireMockRuntimeInfo wm) {
        // No "response" key at all — the field binds to null, same guard as a blank string.
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(okJson("{\"model\":\"llama3.2\"}")));

        assertThatThrownBy(() -> client(wm).complete("llama3.2", "system", "prompt", false))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(SERVICE_UNAVAILABLE);
    }

    @Test
    void complete_serverError_wrapsAsServiceUnavailableNamingTheBaseUrl(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/generate")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client(wm).complete("llama3.2", "system", "prompt", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(wm.getHttpBaseUrl())
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(SERVICE_UNAVAILABLE);
    }

    @Test
    void complete_unreachableHost_wrapsAsServiceUnavailable() {
        // Exercises the same catch-all path a real read timeout would: nothing answers, the
        // client cannot distinguish "slow" from "down", and both fail the same way.
        OllamaLlmClient client = new OllamaLlmClient("http://127.0.0.1:1", 1024, 42, "0");

        assertThatThrownBy(() -> client.complete("llama3.2", "system", "prompt", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AI model is unavailable")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(SERVICE_UNAVAILABLE);
    }

    @Test
    void complete_invalidJsonResponse_wrapsAsServiceUnavailable(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("not json")));

        assertThatThrownBy(() -> client(wm).complete("llama3.2", "system", "prompt", false))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(SERVICE_UNAVAILABLE);
    }

    private static JsonNode sentBody() throws Exception {
        List<LoggedRequest> requests = findAll(postRequestedFor(urlEqualTo("/api/generate")));
        return new ObjectMapper().readTree(requests.get(requests.size() - 1).getBodyAsString());
    }

    private static OllamaLlmClient client(WireMockRuntimeInfo wm) {
        return client(wm, 1024, 42);
    }

    private static OllamaLlmClient client(WireMockRuntimeInfo wm, int numPredict, int seed) {
        return new OllamaLlmClient(wm.getHttpBaseUrl(), numPredict, seed, "0");
    }
}
