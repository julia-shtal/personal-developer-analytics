package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryValidatorTest {

    private final SummaryValidator validator = new SummaryValidator();

    private MetricsSummaryDto.InsightDto insight(String kind, String metric, String explanation) {
        return insight(kind, metric, "text", explanation);
    }

    private MetricsSummaryDto.InsightDto insight(String kind, String metric, String text, String explanation) {
        return MetricsSummaryDto.InsightDto.builder()
                .kind(kind).text(text).metric(metric).explanation(explanation).build();
    }

    /** Identity alias map: each human name maps only to itself. */
    private Map<String, String> aliases(String... humanNames) {
        return Set.of(humanNames).stream()
                .collect(java.util.stream.Collectors.toMap(n -> n, n -> n));
    }

    @Test
    void validate_insightNamesMetricAbsentFromContext_dropsIt() {
        var result = validator.validate(
                List.of(insight("risk", "PR Lead Time", null)),
                aliases("Daily Commits"),
                Set.of(), Set.of());

        assertThat(result.insights()).isEmpty();
        assertThat(result.report().droppedUnknownMetric()).isEqualTo(1);
    }

    @Test
    void validate_insightNamesMetricPresentInContext_keepsIt() {
        var result = validator.validate(
                List.of(insight("positive", "Daily Commits", null)),
                aliases("Daily Commits"),
                Set.of(), Set.of());

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().droppedUnknownMetric()).isZero();
    }

    @Test
    void validate_blankMetric_bypassesTheNameCheck() {
        var result = validator.validate(
                List.of(insight("note", "", null)),
                aliases("Daily Commits"),
                Set.of(), Set.of());

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().droppedUnknownMetric()).isZero();
    }

    @Test
    void validate_anomalousMetricMissingExplanation_dropsIt() {
        var result = validator.validate(
                List.of(insight("risk", "Churn Ratio", null)),
                aliases("Churn Ratio"),
                Set.of("Churn Ratio"), Set.of());

        assertThat(result.insights()).isEmpty();
        assertThat(result.report().droppedMissingExplanation()).isEqualTo(1);
    }

    @Test
    void validate_anomalousMetricWithExplanation_keepsIt() {
        var result = validator.validate(
                List.of(insight("risk", "Churn Ratio", "A large refactor caused the spike.")),
                aliases("Churn Ratio"),
                Set.of("Churn Ratio"), Set.of());

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().droppedMissingExplanation()).isZero();
    }

    @Test
    void validate_nonAnomalousMetricMissingExplanation_keepsIt() {
        var result = validator.validate(
                List.of(insight("positive", "PR Lead Time", null)),
                aliases("PR Lead Time"),
                Set.of(), Set.of());

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().droppedMissingExplanation()).isZero();
    }

    @Test
    void validate_insightCountBelowMinimum_flagsOutOfRange() {
        var result = validator.validate(
                List.of(insight("note", "", null), insight("note", "", null)),
                Map.of(), Set.of(), Set.of());

        assertThat(result.report().finalInsightCount()).isEqualTo(2);
        assertThat(result.report().insightCountInRange()).isFalse();
    }

    @Test
    void validate_insightCountWithinRange_flagsInRange() {
        List<MetricsSummaryDto.InsightDto> five = List.of(
                insight("note", "", null), insight("note", "", null), insight("note", "", null),
                insight("note", "", null), insight("note", "", null));

        var result = validator.validate(five, Map.of(), Set.of(), Set.of());

        assertThat(result.report().finalInsightCount()).isEqualTo(5);
        assertThat(result.report().insightCountInRange()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Metric name aliasing (A5 item 4): enum key accepted, normalised to the human label.
    // -------------------------------------------------------------------------

    @Test
    void validate_insightNamesMetricByEnumKey_keepsItAndNormalisesToHumanLabel() {
        var result = validator.validate(
                List.of(insight("positive", "FOCUS_RATIO_DAYS_TASKS", null)),
                Map.of("Focus Ratio", "Focus Ratio", "FOCUS_RATIO_DAYS_TASKS", "Focus Ratio"),
                Set.of(), Set.of());

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().droppedUnknownMetric()).isZero();
        assertThat(result.insights().get(0).getMetric()).isEqualTo("Focus Ratio");
    }

    @Test
    void validate_insightNamesMetricByHumanLabelAlias_keepsItUnchanged() {
        var result = validator.validate(
                List.of(insight("positive", "Focus Ratio", null)),
                Map.of("Focus Ratio", "Focus Ratio", "FOCUS_RATIO_DAYS_TASKS", "Focus Ratio"),
                Set.of(), Set.of());

        assertThat(result.insights()).hasSize(1);
        assertThat(result.insights().get(0).getMetric()).isEqualTo("Focus Ratio");
    }

    @Test
    void validate_enumKeyAliasOnAnomalousMetric_explanationCheckUsesNormalisedName() {
        // anomalousMetricNames is keyed by the canonical human label; the alias must resolve
        // before that check runs or an enum-key insight would dodge the explanation rule.
        var result = validator.validate(
                List.of(insight("risk", "FOCUS_RATIO_DAYS_TASKS", null)),
                Map.of("Focus Ratio", "Focus Ratio", "FOCUS_RATIO_DAYS_TASKS", "Focus Ratio"),
                Set.of("Focus Ratio"), Set.of());

        assertThat(result.insights()).isEmpty();
        assertThat(result.report().droppedMissingExplanation()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Number groundedness (A5 item 5): flag, never drop or rewrite.
    // -------------------------------------------------------------------------

    @Test
    void validate_numberInTextMatchesAContextValue_notFlagged() {
        var result = validator.validate(
                List.of(insight("note", "Daily Commits", "the median held at 6 commits", null)),
                aliases("Daily Commits"),
                Set.of(), Set.of("6"));

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().ungroundedNumberCount()).isZero();
    }

    @Test
    void validate_numberInTextHasNoMatchingContextValue_isFlaggedButInsightIsKept() {
        var result = validator.validate(
                List.of(insight("note", "Daily Commits", "the median held at 42 commits", null)),
                aliases("Daily Commits"),
                Set.of(), Set.of("6"));

        assertThat(result.insights()).hasSize(1);
        assertThat(result.report().ungroundedNumberCount()).isEqualTo(1);
    }

    @Test
    void validate_textWithNoNumbers_doesNotFlagAnything() {
        var result = validator.validate(
                List.of(insight("note", "Daily Commits", "commit volume stayed steady", null)),
                aliases("Daily Commits"),
                Set.of(), Set.of("6"));

        assertThat(result.report().ungroundedNumberCount()).isZero();
    }

    @Test
    void validate_multipleUngroundedNumbersAcrossInsights_countsEach() {
        var result = validator.validate(
                List.of(
                        insight("note", "Daily Commits", "the median held at 42 commits", null),
                        insight("note", "Daily Commits", "down from 99 last period", null)),
                aliases("Daily Commits"),
                Set.of(), Set.of("6"));

        assertThat(result.report().ungroundedNumberCount()).isEqualTo(2);
    }
}
