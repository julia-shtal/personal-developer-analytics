package com.juliashtal.devanalytics.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsSummaryDto {

    private LocalDate from;
    private LocalDate to;
    /** PERSONAL, REPOSITORY, or TEAM */
    private String scope;
    private String repoName;
    private String overview;
    private List<String> insights;
    private List<String> recommendations;
    private String rawModelOutput;
    private String modelName;
}
