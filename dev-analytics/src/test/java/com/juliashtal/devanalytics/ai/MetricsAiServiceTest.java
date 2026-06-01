package com.juliashtal.devanalytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.juliashtal.devanalytics.ai.client.LlmClient;
import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MetricSummaryPersistenceService;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsAiServiceTest {

    @Mock MetricSnapshotService metricSnapshotService;
    @Mock RepoService repoService;
    @Mock TeamService teamService;
    @Mock UserService userService;
    @Mock LlmClient llmClient;
    @Mock MetricSummaryPersistenceService persistenceService;

    MetricsAiService service;

    private final User user = new User();
    private final LocalDate from = LocalDate.of(2024, 1, 1);
    private final LocalDate to = LocalDate.of(2024, 1, 31);

    private static final String MODEL = "llama3.2";

    @BeforeEach
    void setUp() {
        service = new MetricsAiService(metricSnapshotService, repoService, teamService, userService, llmClient,
                new ObjectMapper().registerModule(new JavaTimeModule()), persistenceService);
        ReflectionTestUtils.setField(service, "model", MODEL);

        // No metric snapshots — context JSON has an empty metrics map.
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void parsesStructuredInsightsAndHeadline() {
        String json = """
                {
                  "headline": "Steady delivery with fast review cycles",
                  "overview": "The developer maintained a consistent commit cadence.",
                  "insights": [
                    { "kind": "positive", "text": "PR lead time improved.", "metric": "PR Lead Time" },
                    { "kind": "risk",     "text": "Churn ratio spiked mid-month.", "metric": "Churn Ratio" }
                  ],
                  "recommendations": ["Review large PRs sooner", "Add integration tests"]
                }
                """;
        when(llmClient.complete(any(), any(), any(), anyBoolean())).thenReturn(json);

        MetricsSummaryDto dto = service.generateSummary(user, from, to, null);

        assertThat(dto.getHeadline()).isEqualTo("Steady delivery with fast review cycles");
        assertThat(dto.getOverview()).isEqualTo("The developer maintained a consistent commit cadence.");
        assertThat(dto.getInsights()).hasSize(2);
        assertThat(dto.getInsights().get(0).getKind()).isEqualTo("positive");
        assertThat(dto.getInsights().get(0).getMetric()).isEqualTo("PR Lead Time");
        assertThat(dto.getInsights().get(1).getKind()).isEqualTo("risk");
        assertThat(dto.getInsights().get(1).getMetric()).isEqualTo("Churn Ratio");
        assertThat(dto.getRecommendations()).containsExactly("Review large PRs sooner", "Add integration tests");
        assertThat(dto.getModelName()).isEqualTo(MODEL);
        assertThat(dto.getScope()).isEqualTo("PERSONAL");
    }

    @Test
    void wrapsFlatsStringsInInsightsAsFallback() {
        // Model returns flat strings in insights array instead of {kind, text, metric} objects.
        String json = """
                {
                  "headline": "Good week overall",
                  "overview": "Metrics look healthy.",
                  "insights": ["High commit volume", "Fast PR reviews"],
                  "recommendations": ["Keep it up"]
                }
                """;
        when(llmClient.complete(any(), any(), any(), anyBoolean())).thenReturn(json);

        MetricsSummaryDto dto = service.generateSummary(user, from, to, null);

        assertThat(dto.getInsights()).hasSize(2);
        dto.getInsights().forEach(insight -> {
            assertThat(insight.getKind()).isEqualTo("note");
            assertThat(insight.getMetric()).isEqualTo("");
        });
        assertThat(dto.getInsights().get(0).getText()).isEqualTo("High commit volume");
        assertThat(dto.getInsights().get(1).getText()).isEqualTo("Fast PR reviews");
    }

    @Test
    void stripsMarkdownFencesBeforeParsing() {
        String json = """
                ```json
                {
                  "headline": "Clean week",
                  "overview": "All metrics within range.",
                  "insights": [],
                  "recommendations": []
                }
                ```""";
        when(llmClient.complete(any(), any(), any(), anyBoolean())).thenReturn(json);

        MetricsSummaryDto dto = service.generateSummary(user, from, to, null);

        assertThat(dto.getHeadline()).isEqualTo("Clean week");
        assertThat(dto.getInsights()).isEmpty();
    }

    @Test
    void returnsRawOutputAsFallbackOnInvalidJson() {
        when(llmClient.complete(any(), any(), any(), anyBoolean())).thenReturn("not valid json at all");

        MetricsSummaryDto dto = service.generateSummary(user, from, to, null);

        assertThat(dto.getOverview()).isEqualTo("not valid json at all");
        assertThat(dto.getHeadline()).isEqualTo("");
        assertThat(dto.getInsights()).isEmpty();
    }
}