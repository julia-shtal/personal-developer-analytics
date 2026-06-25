package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Collects all {@link MetricCalculator} beans and validates at startup that:
 * <ol>
 *   <li>Every {@link MetricType} value is produced by exactly one calculator.</li>
 *   <li>No {@link MetricType} is claimed by two calculators.</li>
 * </ol>
 * A startup failure here is intentional: it prevents silent metric gaps when
 * a new {@link MetricType} value is added without a corresponding calculator.
 */
@Component
@Slf4j
public class MetricCalculatorRegistry {

    private final List<MetricCalculator> calculators;

    public MetricCalculatorRegistry(List<MetricCalculator> calculators) {
        validateCoverage(calculators);
        this.calculators = List.copyOf(calculators);
        log.info("MetricCalculatorRegistry: {} calculators covering {} metric types",
                calculators.size(), MetricType.values().length);
    }

    public Collection<MetricCalculator> all() {
        return calculators;
    }

    private static void validateCoverage(List<MetricCalculator> calculators) {
        EnumSet<MetricType> covered = EnumSet.noneOf(MetricType.class);
        for (MetricCalculator calc : calculators) {
            Set<MetricType> produced = calc.produces();
            for (MetricType type : produced) {
                if (!covered.add(type)) {
                    throw new IllegalStateException(
                            "MetricType." + type + " is claimed by more than one MetricCalculator");
                }
            }
        }
        EnumSet<MetricType> all = EnumSet.allOf(MetricType.class);
        if (!covered.equals(all)) {
            EnumSet<MetricType> missing = EnumSet.copyOf(all);
            missing.removeAll(covered);
            throw new IllegalStateException(
                    "MetricCalculatorRegistry: no calculator registered for " + missing);
        }
    }
}
