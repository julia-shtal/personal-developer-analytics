package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a {@link DataSourceValidationRule} by {@link DataSourceType} at runtime.
 */
@Component
@Slf4j
public class DataSourceValidationRegistry {

    private final Map<DataSourceType, DataSourceValidationRule> registry;

    public DataSourceValidationRegistry(List<DataSourceValidationRule> rules) {
        registry = new EnumMap<>(DataSourceType.class);
        for (DataSourceValidationRule rule : rules) {
            DataSourceType type = rule.supports();
            DataSourceValidationRule previous = registry.put(type, rule);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate DataSourceValidationRule for type " + type
                        + ": " + previous.getClass().getSimpleName()
                        + " and " + rule.getClass().getSimpleName());
            }
        }
        log.info("DataSourceValidationRegistry: registered {} rules: {}", registry.size(), registry.keySet());
    }

    public DataSourceValidationRule forType(DataSourceType type) {
        DataSourceValidationRule rule = registry.get(type);
        if (rule == null) {
            throw new IllegalArgumentException(
                    "No DataSourceValidationRule registered for DataSourceType." + type);
        }
        return rule;
    }
}
