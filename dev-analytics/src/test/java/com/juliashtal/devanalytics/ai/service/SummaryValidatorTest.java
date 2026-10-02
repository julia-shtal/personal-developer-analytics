package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryValidatorTest {

    private final SummaryValidator validator = new SummaryValidator();

    private MetricsSummaryDto.InsightDto insight(String kind, String metric, String explanation) {
        return MetricsSummaryDto.InsightDto.builder()
                .kind(kind).text("text").metric(metric).explanation(explanation).build();
    }

    @Test
    void validate_insightNamesMetricAbsentFromContext_dropsIt() {
        var result = validator.validate(
                List.of(insight("risk", "PR Lead Time", null)),
                Set.of("Daily Commits"),
                Set.of());

        assertThat(result.insights()).isEmpty();
        assertThat(result.report().droppedUnknownMetric()).isEqualTo(1);
    }

    @Test
    void validate_insightNamesMetricPresentInContext_keepsIt() {
        var result = validator.validate(
                List.of(insight("positive", "Daily Commits", null)),
                Set.of("Daily Commits"),
                Set.of());

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().droppedUnknownMetric()).isZero();
    }

    @Test
    void validate_blankMetric_bypassesTheNameCheck() {
        var result = validator.validate(
                List.of(insight("note", "", null)),
                Set.of("Daily Commits"),
                Set.of());

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().droppedUnknownMetric()).isZero();
    }

    @Test
    void validate_anomalousMetricMissingExplanation_dropsIt() {
        var result = validator.validate(
                List.of(insight("risk", "Churn Ratio", null)),
                Set.of("Churn Ratio"),
                Set.of("Churn Ratio"));

        assertThat(result.insights()).isEmpty();
        assertThat(result.report().droppedMissingExplanation()).isEqualTo(1);
    }

    @Test
    void validate_anomalousMetricWithExplanation_keepsIt() {
        var result = validator.validate(
                List.of(insight("risk", "Churn Ratio", "A large refactor caused the spike.")),
                Set.of("Churn Ratio"),
                Set.of("Churn Ratio"));

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().droppedMissingExplanation()).isZero();
    }

    @Test
    void validate_nonAnomalousMetricMissingExplanation_keepsIt() {
        var result = validator.validate(
                List.of(insight("positive", "PR Lead Time", null)),
                Set.of("PR Lead Time"),
                Set.of());

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().droppedMissingExplanation()).isZero();
    }

    @Test
    void validate_insightCountBelowMinimum_flagsOutOfRange() {
        var result = validator.validate(
                List.of(insight("note", "", null), insight("note", "", null)),
                Set.of(),
                Set.of());

        assertThat(result.report().finalInsightCount()).isEqualTo(2);
        assertThat(result.report().insightCountInRange()).isFalse();
    }

    @Test
    void validate_insightCountWithinRange_flagsInRange() {
        List<MetricsSummaryDto.InsightDto> five = List.of(
                insight("note", "", null), insight("note", "", null), insight("note", "", null),
                insight("note", "", null), insight("note", "", null));

        var result = validator.validate(five, Set.of(), Set.of());

        assertThat(result.report().finalInsightCount()).isEqualTo(5);
        assertThat(result.report().insightCountInRange()).isTrue();
    }
}
