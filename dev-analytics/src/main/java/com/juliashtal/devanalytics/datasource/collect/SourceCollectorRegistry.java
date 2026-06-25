package com.juliashtal.devanalytics.datasource.collect;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a {@link SourceCollector} by {@link DataSourceType} at runtime.
 * All {@code SourceCollector} beans in the application context are collected
 * by Spring and validated for uniqueness at startup.
 */
@Component
@Slf4j
public class SourceCollectorRegistry {

    private final Map<DataSourceType, SourceCollector> registry;

    public SourceCollectorRegistry(List<SourceCollector> collectors) {
        registry = new EnumMap<>(DataSourceType.class);
        for (SourceCollector collector : collectors) {
            DataSourceType type = collector.supports();
            SourceCollector previous = registry.put(type, collector);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate SourceCollector for type " + type
                        + ": " + previous.getClass().getSimpleName()
                        + " and " + collector.getClass().getSimpleName());
            }
        }
        log.info("SourceCollectorRegistry: registered {} collectors: {}", registry.size(), registry.keySet());
    }

    /**
     * Returns the collector for the given type.
     *
     * @throws IllegalArgumentException if no collector is registered for {@code type}
     */
    public SourceCollector forType(DataSourceType type) {
        SourceCollector collector = registry.get(type);
        if (collector == null) {
            throw new IllegalArgumentException(
                    "No SourceCollector registered for DataSourceType." + type);
        }
        return collector;
    }
}
