package com.juliashtal.devanalytics.ai.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class MetricsContext {

    private LocalDate from;
    private LocalDate to;
    private String repoName;
    private Map<String, List<DataPoint>> metrics = new LinkedHashMap<>();

    @Data
    public static class DataPoint {
        private final LocalDate date;
        private final double value;
    }
}
