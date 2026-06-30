package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.model.MetricType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricTypeTest {

    @Test
    void inAiContext_exactlyTwelveMetrics() {
        long count = Arrays.stream(MetricType.values()).filter(t -> t.inAiContext).count();
        assertThat(count).as("CONTEXT_METRIC_TYPES size").isEqualTo(12);
    }

    @Test
    void dailySum_exactlyFiveMetrics() {
        long count = Arrays.stream(MetricType.values()).filter(t -> t.dailySum).count();
        assertThat(count).as("DAILY_SUM_METRICS size").isEqualTo(5);
    }

    @Test
    void aggregatePeriod_exactlyFiveMetrics() {
        long count = Arrays.stream(MetricType.values()).filter(t -> t.aggregatePeriod).count();
        assertThat(count).as("AGGREGATE_METRICS size").isEqualTo(5);
    }

    @Test
    void dailySum_isSubsetOfInAiContext() {
        List<MetricType> dailySumNotInContext = Arrays.stream(MetricType.values())
                .filter(t -> t.dailySum && !t.inAiContext)
                .toList();
        assertThat(dailySumNotInContext)
                .as("All dailySum metrics must also have inAiContext=true")
                .isEmpty();
    }

    @Test
    void aggregatePeriod_isSubsetOfInAiContext() {
        List<MetricType> aggregateNotInContext = Arrays.stream(MetricType.values())
                .filter(t -> t.aggregatePeriod && !t.inAiContext)
                .toList();
        assertThat(aggregateNotInContext)
                .as("All aggregatePeriod metrics must also have inAiContext=true")
                .isEmpty();
    }

    @Test
    void totalMetricCount_isTwenty() {
        assertThat(MetricType.values()).hasSize(20);
    }
}
