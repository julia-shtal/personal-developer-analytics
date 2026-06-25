package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.metrics.model.MetricType;

import java.util.Set;

/**
 * Contract for a single metric (or small group of metrics) calculation.
 * Implement one {@code @Component} bean per logical calculation group.
 * {@link MetricCalculatorRegistry} auto-discovers all beans and validates
 * that every {@link MetricType} value is covered exactly once at startup.
 */
public interface MetricCalculator {

    /**
     * The metric types this calculator writes to {@code metric_snapshots}.
     * Multi-metric calculators (e.g. after-hours + refactor) return both types here.
     * Must not be empty; must not overlap with any other registered calculator.
     */
    Set<MetricType> produces();

    /**
     * Performs the calculation and persists results via {@link MetricSnapshotWriter}.
     */
    void calculate(MetricCalcContext ctx);
}
