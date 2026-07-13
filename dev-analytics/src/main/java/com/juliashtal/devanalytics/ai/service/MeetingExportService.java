package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.metrics.model.MemberSummaryDto;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Renders a GitHub-flavoured markdown 1:1 meeting prep document.
 * Output structure: header box → shields.io badge row → AI overview blockquote →
 * diff-fence signals → HTML signal table → checkbox talking points →
 * collapsible next-steps → footer box.
 *
 * Accepts pre-fetched DTOs so the service stays pure and unit-testable.
 */
@Service
@RequiredArgsConstructor
public class MeetingExportService {

    private static final DateTimeFormatter DATE_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Converts LLM-returned metric identifiers (may be enum names like FOCUS_RATIO_DAYS_TASKS
    // or human-readable strings) to display-friendly labels for the report.
    private static final Map<String, String> METRIC_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("DAILY_COMMITS_COUNT",                              "Daily Commits"),
            Map.entry("DAILY_COMMITS_AVG_SIZE",                           "Avg Commit Size"),
            Map.entry("DAILY_PR_CREATED",                                 "PRs Created"),
            Map.entry("DAILY_PR_MERGED",                                  "PRs Merged"),
            Map.entry("DAILY_ISSUES_CREATED",                             "Issues Created"),
            Map.entry("DAILY_ISSUES_CLOSED",                              "Issues Closed"),
            Map.entry("DAILY_CHURN_RATIO",                                "Churn Ratio"),
            Map.entry("PR_LEAD_TIME_HOURS_MEDIAN",                        "PR Lead Time"),
            Map.entry("ISSUE_LEAD_TIME_HOURS_MEDIAN",                     "Issue Lead Time"),
            Map.entry("PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN",  "PR Cycle Time"),
            Map.entry("REVIEW_RESPONSE_TIME_HOURS_MEDIAN",                "Review Response Time"),
            Map.entry("FOCUS_RATIO_DAYS_TASKS",                           "Focus Ratio"),
            Map.entry("AFTER_HOURS_COMMIT_RATIO",                         "After-Hours Commits"),
            Map.entry("DEEP_WORK_STREAK_DAYS",                            "Deep Work Streak"),
            Map.entry("KNOWLEDGE_SILO_SCORE",                             "Knowledge Silo Score"),
            Map.entry("REFACTOR_RATIO",                                   "Refactor Ratio"),
            Map.entry("PR_SIZE_COMPLEXITY_SCORE",                         "PR Complexity"),
            Map.entry("MERGE_WITHOUT_REVIEW_RATIO",                       "Merge Without Review"),
            Map.entry("MERGE_TO_MAIN_FREQUENCY_PER_WEEK",                 "Deploy Frequency")
    );

    // Action prefix per insight kind for the talking-points checkboxes.
    private static final Map<String, String> TP_ACTION = Map.of(
            "positive", "Celebrate:",
            "risk",     "Dig into:",
            "note",     "Track:"
    );

    // shields.io badge colour per metric.
    // Churn is always red (it warrants attention regardless of value).
    private static final Map<MetricType, String> BADGE_COLOR = Map.of(
            MetricType.DAILY_COMMITS_COUNT,       "3FB950",
            MetricType.PR_LEAD_TIME_HOURS_MEDIAN, "8B949E",
            MetricType.DAILY_CHURN_RATIO,         "F85149",
            MetricType.DAILY_PR_MERGED,           "3FB950",
            MetricType.DAILY_ISSUES_CLOSED,       "30363d"
    );

    /**
     * Builds the full markdown string for a 1:1 export.
     *
     * @param member    the team member being exported
     * @param summary   raw metric sums from the snapshot store
     * @param aiSummary AI narrative; null → placeholder rendered
     * @param anomalies anomaly flags per MetricType; used to tag signals
     * @param from      inclusive period start
     * @param to        inclusive period end
     * @param modelName Ollama model identifier
     */
    public String buildMarkdown(User member,
                                MemberSummaryDto summary,
                                MetricsSummaryDto aiSummary,
                                Map<MetricType, Boolean> anomalies,
                                LocalDate from,
                                LocalDate to,
                                String modelName) {
        long days  = ChronoUnit.DAYS.between(from, to) + 1;
        String mdl = modelName != null ? modelName : "llama3.2";

        long   commits  = Math.round(orZero(summary.metrics().get(MetricType.DAILY_COMMITS_COUNT)));
        // PR_LEAD_TIME_HOURS_MEDIAN is an aggregatePeriod metric (single median, not a daily sum).
        double leadTime = orZero(summary.metrics().get(MetricType.PR_LEAD_TIME_HOURS_MEDIAN));
        // Churn is a ratio (0-1 per snapshot), not a daily count — match the UI by using the raw sum directly.
        double churn    = orZero(summary.metrics().get(MetricType.DAILY_CHURN_RATIO));
        long   merged   = Math.round(orZero(summary.metrics().get(MetricType.DAILY_PR_MERGED)));
        long   issues   = Math.round(orZero(summary.metrics().get(MetricType.DAILY_ISSUES_CLOSED)));

        List<MetricsSummaryDto.InsightDto> insights =
                aiSummary != null && aiSummary.getInsights() != null
                        ? aiSummary.getInsights()
                        : List.of();
        List<String> recs =
                aiSummary != null && aiSummary.getRecommendations() != null
                        ? aiSummary.getRecommendations()
                        : List.of();

        StringBuilder sb = new StringBuilder();

        appendHeader(sb, member.getUsername(), from, to, days, mdl);
        appendBadges(sb, commits, leadTime, churn, merged, issues);
        appendOverview(sb, aiSummary);

        sb.append("---\n\n");
        sb.append("## `git log --stat`  ·  the signals\n\n");
        appendDiffFence(sb, insights);
        appendSignalTable(sb, insights, anomalies);

        sb.append("---\n\n");
        sb.append("## Talking points\n\n");
        appendTalkingPoints(sb, insights);
        appendNextSteps(sb, recs);

        sb.append("---\n\n");
        appendFooter(sb, mdl);

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    private void appendHeader(StringBuilder sb, String username,
                              LocalDate from, LocalDate to, long days, String model) {
        sb.append("```console\n");
        sb.append("┌─ 1:1 MEETING PREP ──────────────────────────────────────┐\n");
        sb.append(String.format("│  developer   %-42s│%n", username));
        String windowContent = String.format("%s → %s   (%d days)",
                from.format(DATE_ISO), to.format(DATE_ISO), days);
        sb.append(String.format("│  window      %-42s│%n", windowContent));
        sb.append(String.format("│  generated   %-42s│%n",
                "Developer Analytics · " + model + " @ ollama"));
        sb.append("└──────────────────────────────────────────────────────────┘\n");
        sb.append("```\n\n");
    }

    private void appendBadges(StringBuilder sb,
                              long commits, double leadTime, double churn,
                              long merged, long issues) {
        // Badge colours match BADGE_COLOR map.
        // %25 in URL = literal % shown on the badge; spaces use %20.
        sb.append(String.format(
                "![commits](https://img.shields.io/badge/commits-%d-%s?style=flat-square&labelColor=21262d)%n",
                commits, BADGE_COLOR.get(MetricType.DAILY_COMMITS_COUNT)));
        sb.append(String.format(
                "![lead time](https://img.shields.io/badge/PR_lead_time-%.1fh-%s?style=flat-square&labelColor=21262d)%n",
                leadTime, BADGE_COLOR.get(MetricType.PR_LEAD_TIME_HOURS_MEDIAN)));
        sb.append(String.format(
                "![churn](https://img.shields.io/badge/churn_ratio-%.1f%%25-%s?style=flat-square&labelColor=21262d)%n",
                churn * 100, BADGE_COLOR.get(MetricType.DAILY_CHURN_RATIO)));
        sb.append(String.format(
                "![merged](https://img.shields.io/badge/PRs_merged-%d-%s?style=flat-square&labelColor=21262d)%n",
                merged, BADGE_COLOR.get(MetricType.DAILY_PR_MERGED)));
        sb.append(String.format(
                "![issues](https://img.shields.io/badge/issues_closed-%d-%s?style=flat-square&labelColor=21262d)%n",
                issues, BADGE_COLOR.get(MetricType.DAILY_ISSUES_CLOSED)));
        sb.append("\n");
    }

    private void appendOverview(StringBuilder sb, MetricsSummaryDto ai) {
        if (ai == null) {
            sb.append("> *AI summary not available.*\n\n");
            return;
        }
        if (ai.getHeadline() != null) {
            sb.append("> ### ").append(ai.getHeadline()).append("\n");
        }
        if (ai.getOverview() != null) {
            sb.append("> ").append(ai.getOverview()).append("\n");
        }
        sb.append("\n");
    }

    /** diff fence: + for positive, - for risk, ! for note. */
    private void appendDiffFence(StringBuilder sb, List<MetricsSummaryDto.InsightDto> insights) {
        sb.append("```diff\n");
        for (MetricsSummaryDto.InsightDto insight : insights) {
            String prefix = switch (insight.getKind()) {
                case "positive" -> "+ ";
                case "risk"     -> "- ";
                default         -> "! ";
            };
            sb.append(prefix).append(insight.getText()).append("\n");
        }
        if (insights.isEmpty()) {
            sb.append("  (no signals)\n");
        }
        sb.append("```\n\n");
    }

    /** HTML signal table — Signal / Read / Tag columns. */
    private void appendSignalTable(StringBuilder sb,
                                   List<MetricsSummaryDto.InsightDto> insights,
                                   Map<MetricType, Boolean> anomalies) {
        sb.append("<table>\n");
        sb.append("<tr><th align=\"left\">Signal</th><th align=\"left\">Read</th><th align=\"left\">Tag</th></tr>\n");
        for (MetricsSummaryDto.InsightDto insight : insights) {
            String tag = kindTag(insight.getKind());
            String read = escapeHtml(insight.getText());
            if (insight.getExplanation() != null) {
                read += " <em>" + escapeHtml(insight.getExplanation()) + "</em>";
            }
            sb.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td></tr>%n",
                    escapeHtml(displayName(insight.getMetric())), read, tag));
        }
        sb.append("</table>\n\n");
    }

    /** Checkbox talking points from insights. */
    private void appendTalkingPoints(StringBuilder sb, List<MetricsSummaryDto.InsightDto> insights) {
        for (MetricsSummaryDto.InsightDto insight : insights) {
            String action = TP_ACTION.getOrDefault(insight.getKind(), "Discuss:");
            String metric = insight.getMetric() != null && !insight.getMetric().isBlank()
                    ? displayName(insight.getMetric()) : "this signal";
            sb.append(String.format("- [ ] **%s %s** %s%n", action, metric, insight.getText()));
        }
        sb.append("\n");
    }

    /** Collapsible next-steps block from AI recommendations. */
    private void appendNextSteps(StringBuilder sb, List<String> recs) {
        sb.append("<details>\n");
        sb.append("<summary><b>📋 Suggested next steps</b> — tap to expand</summary>\n\n");
        int i = 1;
        for (String rec : recs) {
            int dot = rec.indexOf(". ");
            if (dot > 0 && dot < rec.length() - 2) {
                String title = rec.substring(0, dot + 1);
                String body  = rec.substring(dot + 2);
                sb.append(String.format("%d. **%s** %s%n", i++, title, body));
            } else {
                sb.append(String.format("%d. %s%n", i++, rec));
            }
        }
        if (recs.isEmpty()) {
            sb.append("No recommendations generated.\n");
        }
        sb.append("\n</details>\n\n");
    }

    private void appendFooter(StringBuilder sb, String model) {
        sb.append("```\n");
        sb.append("─────────────────────────────────────────────────────────────\n");
        sb.append(String.format("  generated by Developer Analytics · %s via Ollama%n", model));
        sb.append("  metrics are descriptive, not evaluative — context first\n");
        sb.append("─────────────────────────────────────────────────────────────\n");
        sb.append("```\n");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Maps insight kind to a coloured dot tag string. */
    private String kindTag(String kind) {
        return switch (kind) {
            case "positive" -> "🟢 good";
            case "risk"     -> "🔴 watch";
            default         -> "⚪ note";
        };
    }

    private double avg(Double raw, long days) {
        return raw != null && days > 0 ? raw / days : 0.0;
    }

    private double orZero(Double v) {
        return v != null ? v : 0.0;
    }

    /** Returns a human-readable label for a metric identifier (handles enum names and display names). */
    private String displayName(String metric) {
        if (metric == null || metric.isBlank()) return "Signal";
        return METRIC_DISPLAY_NAMES.getOrDefault(metric, metric);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
