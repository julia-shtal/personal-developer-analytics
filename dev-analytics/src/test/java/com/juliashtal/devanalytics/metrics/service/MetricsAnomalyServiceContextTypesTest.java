package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.metrics.model.MetricType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the anomaly service's context list to the {@code inAiContext} flag.
 * <p>{@code AiContextBuilderServiceTest} pins the builder's list to the same flag, so the two
 * declarations cannot drift apart: an anomaly flag on a metric absent from the prompt, or a
 * context metric that can never be flagged, both fail here.</p>
 */
class MetricsAnomalyServiceContextTypesTest {

    @Test
    void contextMetricTypes_matchesInAiContextFlag_inBothDirections() {
        List<MetricType> flagged = Arrays.stream(MetricType.values())
                .filter(t -> t.inAiContext)
                .toList();

        assertThat(MetricsAnomalyService.CONTEXT_METRIC_TYPES)
                .containsExactlyInAnyOrderElementsOf(flagged);
    }
}
