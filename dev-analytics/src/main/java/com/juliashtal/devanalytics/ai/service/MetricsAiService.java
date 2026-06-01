package com.juliashtal.devanalytics.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.ai.client.LlmClient;
import com.juliashtal.devanalytics.ai.model.AggregatedMetricsContext;
import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.model.TeamMetricsContext;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsAiService {

    private static final List<MetricType> CONTEXT_METRIC_TYPES = List.of(
            DAILY_COMMITS_COUNT,
            DAILY_PR_CREATED,
            DAILY_PR_MERGED,
            DAILY_ISSUES_CREATED,
            DAILY_ISSUES_CLOSED,
            DAILY_CHURN_RATIO,
            PR_LEAD_TIME_HOURS_MEDIAN,
            PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
            ISSUE_LEAD_TIME_HOURS_MEDIAN,
            REVIEW_RESPONSE_TIME_HOURS_MEDIAN,
            FOCUS_RATIO_DAYS_TASKS
    );

    private static final Set<MetricType> DAILY_SUM_METRICS = Set.of(
            DAILY_COMMITS_COUNT, DAILY_PR_CREATED, DAILY_PR_MERGED,
            DAILY_ISSUES_CREATED, DAILY_ISSUES_CLOSED
    );

    // Metrics stored with periodFrom/periodTo (not date-series) — must use exact-period query.
    // FOCUS_RATIO_DAYS_TASKS is excluded: it is stored as per-day markers (periodFrom/To = null)
    // and its aggregate is computed on the read side by counting markers in the date range.
    private static final Set<MetricType> AGGREGATE_METRICS = Set.of(
            PR_LEAD_TIME_HOURS_MEDIAN,
            PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
            ISSUE_LEAD_TIME_HOURS_MEDIAN,
            REVIEW_RESPONSE_TIME_HOURS_MEDIAN
    );

    private final MetricSnapshotService metricSnapshotService;
    private final RepoService repoService;
    private final TeamService teamService;
    private final UserService userService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.ollama.model:llama3}")
    private String model;

    // -------------------------------------------------------------------------
    // Personal / repository summary
    // -------------------------------------------------------------------------

    @Cacheable(value = "ai_summaries", key = "{#user.id, #from, #to, #repoId}")
    public MetricsSummaryDto generateSummary(User user, LocalDate from, LocalDate to, Long repoId) {
        GitRepositoryEntity repo = repoId != null ? repoService.getById(repoId) : null;

        AggregatedMetricsContext ctx = buildMetricsContext(user, from, to, repo);
        String ctxJson = toJson(ctx);

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(from, to, repo, ctxJson);

        log.info("Generating AI summary: userId={}, scope={}, from={}, to={}, model={}, promptLen={}",
                user.getId(), repo != null ? "REPOSITORY" : "PERSONAL", from, to, model, userPrompt.length());

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt, true);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("AI summary generated: userId={}, durationMs={}, responseLen={}", user.getId(), durationMs, raw.length());

        return parseSummary(raw, from, to, repo != null ? "REPOSITORY" : "PERSONAL",
                repo != null ? repo.getName() : null);
    }

    // -------------------------------------------------------------------------
    // Team summary
    // -------------------------------------------------------------------------

    @Cacheable(value = "ai_summaries", key = "{'team', #teamId, #from, #to}")
    public MetricsSummaryDto generateTeamSummary(User requestingUser, Long teamId, LocalDate from, LocalDate to) {
        Team team = teamService.getById(teamId);

        if (requestingUser.getRole() != Role.ADMIN
                && !team.getManager().getId().equals(requestingUser.getId())) {
            throw new ForbiddenException("Only the team manager or an admin can generate team AI summaries");
        }

        TeamMetricsContext ctx = buildTeamMetricsContext(team, from, to);
        String ctxJson = toJson(ctx);

        String systemPrompt = buildTeamSystemPrompt();
        String userPrompt = buildTeamUserPrompt(from, to, team.getName(), ctxJson);

        log.info("Generating team AI summary: teamId={}, teamName={}, members={}, from={}, to={}, model={}",
                teamId, team.getName(), team.getMembers().size(), from, to, model);

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt, true);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("Team AI summary generated: teamId={}, durationMs={}, responseLen={}", teamId, durationMs, raw.length());

        return parseSummary(raw, from, to, "TEAM", team.getName());
    }

    // -------------------------------------------------------------------------
    // Member summary (manager-scoped, cached per member+period)
    // -------------------------------------------------------------------------

    @Cacheable(value = "ai_summaries", key = "{'member', #teamId, #memberId, #from, #to}")
    public MetricsSummaryDto generateMemberSummary(User requestingUser, Long teamId, Long memberId,
                                                    LocalDate from, LocalDate to) {
        Team team = teamService.getById(teamId);

        if (requestingUser.getRole() != Role.ADMIN
                && !team.getManager().getId().equals(requestingUser.getId())) {
            throw new ForbiddenException("Only the team manager or an admin can generate member AI summaries");
        }

        User member = userService.getById(memberId);

        AggregatedMetricsContext ctx = buildMetricsContext(member, from, to, null);
        String ctxJson = toJson(ctx);

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(from, to, null, ctxJson);

        log.info("Generating member AI summary: requesterId={}, teamId={}, memberId={}, from={}, to={}, model={}",
                requestingUser.getId(), teamId, memberId, from, to, model);

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt, true);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("Member AI summary generated: memberId={}, durationMs={}, responseLen={}", memberId, durationMs, raw.length());

        return parseSummary(raw, from, to, "PERSONAL", member.getUsername());
    }

    // -------------------------------------------------------------------------
    // Context building — personal (pre-aggregated)
    // -------------------------------------------------------------------------

    private AggregatedMetricsContext buildMetricsContext(User user, LocalDate from, LocalDate to,
                                                         GitRepositoryEntity repo) {
        Map<String, AggregatedMetricsContext.MetricAggregate> aggregates = new LinkedHashMap<>();

        for (MetricType type : CONTEXT_METRIC_TYPES) {
            List<MetricSnapshot> snapshots;
            boolean isAggregate = AGGREGATE_METRICS.contains(type);
            if (repo != null) {
                snapshots = isAggregate
                        ? metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo(user, type, repo, from, to)
                        : metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(user, type, repo, from, to);
            } else {
                snapshots = isAggregate
                        ? metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(user, type, from, to)
                        : metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, type, from, to);
            }

            if (!snapshots.isEmpty()) {
                List<Double> values = snapshots.stream()
                        .sorted(Comparator.comparing(MetricSnapshot::getDate))
                        .map(MetricSnapshot::getValue)
                        .collect(Collectors.toList());
                aggregates.put(type.name(), computeAggregate(values, isDailySumMetric(type)));
            }
        }

        AggregatedMetricsContext ctx = new AggregatedMetricsContext();
        ctx.setFrom(from);
        ctx.setTo(to);
        ctx.setRepoName(repo != null ? repo.getName() : null);
        ctx.setMetrics(aggregates);
        return ctx;
    }

    private AggregatedMetricsContext.MetricAggregate computeAggregate(List<Double> values, boolean isSumMetric) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();

        double min = sorted.get(0);
        double max = sorted.get(n - 1);
        double median = n % 2 == 1
                ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        long total = isSumMetric ? Math.round(values.stream().mapToDouble(Double::doubleValue).sum()) : 0L;

        double trendPct = computeTrendPct(values);
        boolean anomaly = hasAnomaly(values);

        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.HALF_UP);

        return AggregatedMetricsContext.MetricAggregate.builder()
                .min(df.format(min))
                .max(df.format(max))
                .median(df.format(median))
                .total(total)
                .trendPct(trendPct)
                .anomaly(anomaly)
                .build();
    }

    private double computeTrendPct(List<Double> chronological) {
        int n = chronological.size();
        if (n < 2) return 0.0;
        int half = n / 2;
        double earlyAvg = chronological.subList(0, half).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double recentAvg = chronological.subList(n - half, n).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (earlyAvg == 0) return 0.0;
        return Math.round(((recentAvg - earlyAvg) / earlyAvg) * 1000.0) / 10.0;
    }

    private boolean hasAnomaly(List<Double> values) {
        int n = values.size();
        if (n < 3) return false;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        return values.stream().anyMatch(v -> Math.abs(v - mean) > 2 * stdDev);
    }

    // -------------------------------------------------------------------------
    // Context building — team
    // -------------------------------------------------------------------------

    private TeamMetricsContext buildTeamMetricsContext(Team team, LocalDate from, LocalDate to) {
        List<TeamMetricsContext.MemberMetrics> memberMetricsList = new ArrayList<>();

        for (User member : team.getMembers()) {
            TeamMetricsContext.MemberMetrics mm = new TeamMetricsContext.MemberMetrics();
            mm.setUsername(member.getUsername());

            Map<String, Double> aggregated = new LinkedHashMap<>();
            for (MetricType type : CONTEXT_METRIC_TYPES) {
                List<MetricSnapshot> snapshots = metricSnapshotService
                        .getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(member, team, type, from, to);
                if (!snapshots.isEmpty()) {
                    double value = isDailySumMetric(type)
                            ? snapshots.stream().mapToDouble(MetricSnapshot::getValue).sum()
                            : snapshots.stream().mapToDouble(MetricSnapshot::getValue).average().orElse(0.0);
                    aggregated.put(type.name(), value);
                }
            }
            mm.setMetrics(aggregated);
            memberMetricsList.add(mm);
        }

        TeamMetricsContext ctx = new TeamMetricsContext();
        ctx.setFrom(from);
        ctx.setTo(to);
        ctx.setTeamName(team.getName());
        ctx.setMemberCount(team.getMembers().size());
        ctx.setMembers(memberMetricsList);
        return ctx;
    }

    // -------------------------------------------------------------------------
    // Prompts — personal / repository
    // -------------------------------------------------------------------------

    private String buildSystemPrompt() {
        return """
                You are a developer analytics assistant analysing metrics for a SINGLE individual developer.
                Do NOT mention teams, team members, other developers, or comparisons to other people.
                Analyze the provided metrics JSON and return ONLY a valid JSON object.
                Do not include any markdown, code fences, explanations, or text outside the JSON.

                Metric name mapping:
                - DAILY_COMMITS_COUNT: "Daily Commits"
                - DAILY_PR_CREATED: "PRs Created"
                - DAILY_PR_MERGED: "Merged PRs"
                - DAILY_ISSUES_CREATED: "Issues Created"
                - DAILY_ISSUES_CLOSED: "Issues Closed"
                - DAILY_CHURN_RATIO: "Churn Ratio"
                - PR_LEAD_TIME_HOURS_MEDIAN: "PR Lead Time"
                - PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN: "First Commit to Merge"
                - ISSUE_LEAD_TIME_HOURS_MEDIAN: "Issue Lead Time"
                - REVIEW_RESPONSE_TIME_HOURS_MEDIAN: "Review Response Time"
                - FOCUS_RATIO_DAYS_TASKS: "Focus Ratio"

                Each metric has: min, max, median, total (count metrics only), trendPct (% change recent vs early), anomaly (boolean).

                Required output format (JSON only, no other text):
                {
                  "headline": "one sentence editorial title for the period",
                  "overview": "1-2 sentence summary",
                  "insights": [
                    { "kind": "positive", "text": "...", "metric": "PR Lead Time" },
                    { "kind": "risk",     "text": "...", "metric": "Knowledge Silo" },
                    { "kind": "note",     "text": "...", "metric": "Churn Ratio" }
                  ],
                  "recommendations": ["action 1", "action 2", "action 3"]
                }

                Rules for insight "kind":
                - "positive" — the metric is healthy or improving.
                - "risk"     — the metric signals a problem that needs attention.
                - "note"     — neutral observation, neither clearly good nor bad.

                Rules for insight "metric":
                - Must be one of the human-readable metric names from the mapping above.

                Rules for insights (follow this order strictly):
                1. Check Churn Ratio and PR Lead Time first — they are primary quality indicators.
                2. Check Focus Ratio and Daily Commits second — they are primary throughput indicators.
                3. Any metric with anomaly: true MUST be included as an insight.
                4. Then cover remaining metrics (review response time, issue lead time, PRs created/merged).
                5. Reference concrete values (median, trendPct, anomaly) in every insight.

                General rules:
                - headline: one editorial sentence capturing the defining characteristic of the period.
                - overview: 1-2 sentences on delivery flow, cycle efficiency, and key patterns.
                - insights: 5-8 items, in the priority order above.
                - recommendations: 3-5 actionable items backed by the data.
                - Use only the provided data. Do not speculate beyond the metrics.
                - Avoid buzzwords and generic motivational phrasing.

                Rules for numbers:
                - All decimal values in the JSON are pre-rounded; use them exactly as provided.
                - Totals are whole numbers; do not add decimal places.
                - Express time metrics in hours (e.g., "22 hours", not "22.0 hours").
                - Express trend as a percentage with one decimal (e.g., "-19.3%", not "-19.3000%").
                """;
    }

    private String buildUserPrompt(LocalDate from, LocalDate to, GitRepositoryEntity repo, String ctxJson) {
        String scopeInfo = repo != null ? " for repository " + repo.getName() : "";
        return """
                Analyze the following INDIVIDUAL developer productivity metrics for the period %s to %s%s.
                This is a personal analysis — do not mention teams or other developers.
                Return ONLY the JSON object as specified. No markdown, no extra text.

                Metrics JSON:
                %s
                """.formatted(from, to, scopeInfo, ctxJson);
    }

    // -------------------------------------------------------------------------
    // Prompts — team
    // -------------------------------------------------------------------------

    private String buildTeamSystemPrompt() {
        return """
                You are a developer analytics assistant analyzing metrics for a SOFTWARE DEVELOPMENT TEAM.
                Analyze the provided team metrics JSON and return ONLY a valid JSON object.
                Do not include any markdown, code fences, explanations, or text outside the JSON.

                Metric name mapping:
                - DAILY_COMMITS_COUNT: "Daily Commits"
                - DAILY_PR_CREATED: "PRs Created"
                - DAILY_PR_MERGED: "Merged PRs"
                - DAILY_ISSUES_CREATED: "Issues Created"
                - DAILY_ISSUES_CLOSED: "Issues Closed"
                - DAILY_CHURN_RATIO: "Churn Ratio"
                - PR_LEAD_TIME_HOURS_MEDIAN: "PR Lead Time"
                - PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN: "First Commit to Merge"
                - ISSUE_LEAD_TIME_HOURS_MEDIAN: "Issue Lead Time"
                - REVIEW_RESPONSE_TIME_HOURS_MEDIAN: "Review Response Time"
                - FOCUS_RATIO_DAYS_TASKS: "Focus Ratio"

                Each member has a "metrics" map of aggregated values for the period.

                Required output format (JSON only, no other text):
                {
                  "headline": "one sentence editorial title capturing the team's defining characteristic for the period",
                  "overview": "1-2 sentence team summary",
                  "insights": [
                    { "kind": "positive", "text": "...", "metric": "PR Lead Time" },
                    { "kind": "risk",     "text": "...", "metric": "Knowledge Silo" },
                    { "kind": "note",     "text": "...", "metric": "Churn Ratio" }
                  ],
                  "recommendations": ["action 1", "action 2", "action 3"]
                }

                Rules for insight "kind":
                - "positive" — the metric is healthy or improving across the team.
                - "risk"     — the metric signals a problem that needs team attention.
                - "note"     — neutral observation about team patterns, neither clearly good nor bad.

                Rules for insight "metric":
                - Must be one of the human-readable metric names from the mapping above.

                Rules for insights (follow this order strictly):
                1. Check Churn Ratio and PR Lead Time first — they are primary quality indicators across members.
                2. Check Focus Ratio and Daily Commits second — they are primary throughput indicators.
                3. Identify cross-member outliers (highest/lowest values) for each quality and throughput metric.
                4. Then cover remaining metrics (review response time, issue lead time, PRs created/merged).
                5. Reference member usernames and concrete values in every insight.

                General rules:
                - headline: one editorial sentence capturing the team's defining characteristic for the period.
                - overview: 1-2 sentences on team delivery flow and collaboration.
                - insights: 5-8 items, in the priority order above.
                - recommendations: 3-5 actionable team process improvements backed by the data.
                - Use only the provided data. Do not speculate beyond the metrics.
                - Avoid generic team language and motivational phrasing.

                Rules for numbers:
                - All decimal values in the JSON are pre-rounded; use them exactly as provided.
                - Totals are whole numbers; do not add decimal places.
                - Express time metrics in hours (e.g., "22 hours", not "22.0 hours").
                - Express trend as a percentage with one decimal (e.g., "-19.3%", not "-19.3000%").
                """;
    }

    private String buildTeamUserPrompt(LocalDate from, LocalDate to, String teamName, String ctxJson) {
        return """
                Analyze the following team productivity metrics for team "%s" for the period %s to %s.
                Return ONLY the JSON object as specified. No markdown, no extra text.

                Team Metrics JSON:
                %s
                """.formatted(teamName, from, to, ctxJson);
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private MetricsSummaryDto parseSummary(String raw, LocalDate from, LocalDate to,
                                           String scope, String scopeName) {
        String cleaned = raw.strip();
        // Strip markdown code fences that models sometimes add despite instructions
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").strip();
        }

        try {
            JsonNode root = objectMapper.readTree(cleaned);

            String headline = root.path("headline").asText("").strip();
            String overview = root.path("overview").asText("").strip();

            // Insights: tolerate flat strings from models that ignore the structured format.
            // A flat string is wrapped as InsightDto("note", text, "") so parsing never breaks.
            List<MetricsSummaryDto.InsightDto> insights = new ArrayList<>();
            JsonNode insightsNode = root.path("insights");
            if (insightsNode.isArray()) {
                for (JsonNode node : insightsNode) {
                    if (node.isTextual()) {
                        insights.add(MetricsSummaryDto.InsightDto.builder()
                                .kind("note").text(node.asText()).metric("").build());
                    } else if (node.isObject()) {
                        insights.add(MetricsSummaryDto.InsightDto.builder()
                                .kind(node.path("kind").asText("note"))
                                .text(node.path("text").asText(""))
                                .metric(node.path("metric").asText(""))
                                .build());
                    }
                }
            }

            List<String> recommendations = new ArrayList<>();
            JsonNode recsNode = root.path("recommendations");
            if (recsNode.isArray()) {
                for (JsonNode node : recsNode) {
                    recommendations.add(node.asText());
                }
            }

            return MetricsSummaryDto.builder()
                    .from(from)
                    .to(to)
                    .scope(scope)
                    .contextRepoName(scopeName)
                    .headline(headline)
                    .overview(overview)
                    .insights(insights)
                    .recommendations(recommendations)
                    .rawModelOutput(raw)
                    .modelName(model)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI JSON output, returning raw text as overview. Error: {}", e.getMessage());
            return MetricsSummaryDto.builder()
                    .from(from)
                    .to(to)
                    .scope(scope)
                    .contextRepoName(scopeName)
                    .headline("")
                    .overview(raw)
                    .insights(List.of())
                    .recommendations(List.of())
                    .rawModelOutput(raw)
                    .modelName(model)
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private boolean isDailySumMetric(MetricType type) {
        return DAILY_SUM_METRICS.contains(type);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize metrics context", e);
        }
    }
}
