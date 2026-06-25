package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.metrics.model.MetricType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricCalculatorRegistryTest {

    private static MetricCalculator stubFor(MetricType... types) {
        return new MetricCalculator() {
            @Override public Set<MetricType> produces() { return Set.of(types); }
            @Override public void calculate(MetricCalcContext ctx) {}
        };
    }

    private static List<MetricCalculator> onePerType() {
        return Arrays.stream(MetricType.values())
                .map(t -> stubFor(t))
                .collect(Collectors.toList());
    }

    @Test
    void constructor_allTypesCoveredOnce_succeeds() {
        MetricCalculatorRegistry registry = new MetricCalculatorRegistry(onePerType());
        assertThat(registry.all()).hasSize(MetricType.values().length);
    }

    @Test
    void constructor_missingType_throwsIllegalStateException() {
        List<MetricCalculator> incomplete = new ArrayList<>(onePerType());
        incomplete.remove(incomplete.size() - 1);

        assertThatThrownBy(() -> new MetricCalculatorRegistry(incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no calculator registered");
    }

    @Test
    void constructor_duplicateType_throwsIllegalStateException() {
        MetricType dup = MetricType.DAILY_COMMITS_COUNT;
        List<MetricCalculator> list = new ArrayList<>(onePerType());
        list.add(stubFor(dup));

        assertThatThrownBy(() -> new MetricCalculatorRegistry(list))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(dup.name());
    }
}
