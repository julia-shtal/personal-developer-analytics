package com.juliashtal.devanalytics.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the configured timeouts reach the request factory, so the shared RestTemplate cannot
 * wait forever on an unresponsive host.
 *
 * <p>The factory exposes no getters, so its fields are the only reachable evidence; a rename
 * there fails this test loudly rather than silently restoring the unlimited default.</p>
 */
class RestTemplateConfigTest {

    @Test
    void restTemplate_configuredTimeouts_reachTheRequestFactory() {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate(1_500, 9_000);

        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        assertThat(factory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(1_500);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(9_000);
    }

    @Test
    void restTemplate_applicationDefaults_areFiniteAndPositive() {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate(5_000, 30_000);

        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
        assertThat((Integer) ReflectionTestUtils.getField(factory, "connectTimeout")).isPositive();
        assertThat((Integer) ReflectionTestUtils.getField(factory, "readTimeout")).isPositive();
    }
}
