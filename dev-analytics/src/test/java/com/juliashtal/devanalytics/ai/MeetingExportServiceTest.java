package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MeetingExportService;
import com.juliashtal.devanalytics.metrics.model.MemberSummaryDto;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingExportServiceTest {

    private MeetingExportService service;

    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 6, 30); // 30 days

    @BeforeEach
    void setUp() {
        service = new MeetingExportService();
    }

    private User member(String username) {
        User u = new User();
        u.setId(1L);
        u.setUsername(username);
        u.setEmail("test@example.com");
        u.setLastActiveAt(Instant.now());
        return u;
    }

    /** Summary values as the controller delivers them:
     *  COMMITS/PRs/ISSUES are sums over the window; CHURN is a sum of daily ratios;
     *  aggregatePeriod metrics (LEAD_TIME) are single aggregate values. */
    private MemberSummaryDto summaryDto() {
        Map<MetricType, Double> metrics = Map.of(
                MetricType.DAILY_COMMITS_COUNT,       90.0,   // total commits in period
                MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 12.5,   // single aggregate median (not a sum)
                MetricType.DAILY_CHURN_RATIO,         0.25,   // raw ratio sum → displayed as 25.0 %
                MetricType.DAILY_PR_MERGED,           15.0,
                MetricType.DAILY_ISSUES_CLOSED,       8.0
        );
        return new MemberSummaryDto(1L, "alice", metrics, false, null, Instant.now(), "alice@example.com");
    }

    private MetricsSummaryDto aiSummary() {
        return MetricsSummaryDto.builder()
                .headline("Steady output this sprint")
                .overview("Developer maintained consistent velocity.")
                .insights(List.of(
                        MetricsSummaryDto.InsightDto.builder()
                                .kind("positive").text("PR lead time improved by 19%.").metric("PR Lead Time").build(),
                        MetricsSummaryDto.InsightDto.builder()
                                .kind("risk").text("Churn ratio is elevated.").metric("Churn Ratio")
                                .explanation("A large refactor drove the spike.").build(),
                        MetricsSummaryDto.InsightDto.builder()
                                .kind("note").text("Daily commits are stable.").metric("Daily Commits").build()
                ))
                .recommendations(List.of(
                        "Keep review momentum. Hold the PR turnaround; flag if it starts to slip.",
                        "Trace the churn."
                ))
                .modelName("llama3.2")
                .build();
    }

    @Test
    void buildMarkdown_header_containsUsernameWindowAndModel() {
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("developer   alice");
        assertThat(md).contains("2026-06-01 → 2026-06-30");
        assertThat(md).contains("30 days");
        assertThat(md).contains("llama3.2 @ ollama");
    }

    @Test
    void buildMarkdown_badges_churnFormattedAsPercent() {
        // raw ratio 0.25 → 25.0 %
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("churn_ratio-25.0%25");
    }

    @Test
    void buildMarkdown_badges_commitsShowsTotal() {
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("commits-90");
    }

    @Test
    void buildMarkdown_overview_renderedAsBlockquote() {
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("> ### Steady output this sprint");
        assertThat(md).contains("> Developer maintained consistent velocity.");
    }

    @Test
    void buildMarkdown_diffFence_prefixesByKind() {
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("+ PR lead time improved by 19%.");
        assertThat(md).contains("- Churn ratio is elevated.");
        assertThat(md).contains("! Daily commits are stable.");
    }

    @Test
    void buildMarkdown_signalTable_containsMetricReadAndTag() {
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("<td>PR Lead Time</td>");
        assertThat(md).contains("🟢 good");
        assertThat(md).contains("🔴 watch");
        assertThat(md).contains("⚪ note");
    }

    @Test
    void buildMarkdown_signalTable_explanationIncludedInRead() {
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("A large refactor drove the spike.");
    }

    @Test
    void buildMarkdown_talkingPoints_checkboxesWithActionPrefix() {
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("- [ ] **Celebrate: PR Lead Time**");
        assertThat(md).contains("- [ ] **Dig into: Churn Ratio**");
        assertThat(md).contains("- [ ] **Track: Daily Commits**");
    }

    @Test
    void buildMarkdown_nextSteps_collapsibleWithSplitTitle() {
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("<details>");
        assertThat(md).contains("📋 Suggested next steps");
        // first rec splits at ". " → bold title + body
        assertThat(md).contains("**Keep review momentum.**");
        assertThat(md).contains("Hold the PR turnaround; flag if it starts to slip.");
    }

    @Test
    void buildMarkdown_nullAiSummary_rendersPlaceholder() {
        String md = service.buildMarkdown(member("carol"), summaryDto(), null,
                Map.of(), FROM, TO, null);

        assertThat(md).contains("*AI summary not available.*");
        assertThat(md).contains("llama3.2 via Ollama");
    }

    @Test
    void buildMarkdown_footer_containsAttributionAndDisclaimer() {
        String md = service.buildMarkdown(member("alice"), summaryDto(), aiSummary(),
                Map.of(), FROM, TO, "llama3.2");

        assertThat(md).contains("llama3.2 via Ollama");
        assertThat(md).contains("metrics are descriptive, not evaluative");
    }
}
