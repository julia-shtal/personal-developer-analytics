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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsAiService {

    private final AiContextBuilderService contextBuilder;
    private final RepoService repoService;
    private final TeamService teamService;
    private final UserService userService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final MetricSummaryPersistenceService persistenceService;

    @Value("${ai.ollama.model:llama3}")
    private String model;

    // -------------------------------------------------------------------------
    // Personal / repository summary
    // -------------------------------------------------------------------------

    @Cacheable(value = "ai_summaries", key = "{#user.id, #from, #to, #repoId}")
    public MetricsSummaryDto generateSummary(User user, LocalDate from, LocalDate to, Long repoId) {
        GitRepositoryEntity repo = repoId != null ? repoService.getById(repoId) : null;

        AggregatedMetricsContext ctx = contextBuilder.buildPersonalContext(user, from, to, repo);
        String ctxJson = toJson(ctx);

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(from, to, repo, ctxJson);

        log.info("Generating AI summary: userId={}, scope={}, from={}, to={}, model={}, promptLen={}",
                user.getId(), repo != null ? "REPOSITORY" : "PERSONAL", from, to, model, userPrompt.length());

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt, true);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("AI summary generated: userId={}, durationMs={}, responseLen={}", user.getId(), durationMs, raw.length());

        MetricsSummaryDto dto = parseSummary(raw, from, to, repo != null ? "REPOSITORY" : "PERSONAL",
                repo != null ? repo.getName() : null);
        persistenceService.savePersonal(user, dto);
        return dto;
    }

    // -------------------------------------------------------------------------
    // Team summary
    // -------------------------------------------------------------------------

    @Cacheable(value = "ai_summaries", key = "{'team', #teamId, #from, #to}")
    public MetricsSummaryDto generateTeamSummary(User requestingUser, Long teamId, LocalDate from, LocalDate to) {
        Team team = teamService.getById(teamId);

        assertManagerOrAdmin(requestingUser, team);

        TeamMetricsContext ctx = contextBuilder.buildTeamContext(team, from, to);
        String ctxJson = toJson(ctx);

        String systemPrompt = buildTeamSystemPrompt();
        String userPrompt = buildTeamUserPrompt(from, to, team.getName(), ctxJson);

        log.info("Generating team AI summary: teamId={}, teamName={}, members={}, from={}, to={}, model={}",
                teamId, team.getName(), team.getMembers().size(), from, to, model);

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt, true);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("Team AI summary generated: teamId={}, durationMs={}, responseLen={}", teamId, durationMs, raw.length());

        MetricsSummaryDto dto = parseSummary(raw, from, to, "TEAM", team.getName());
        persistenceService.saveTeam(team, dto);
        return dto;
    }

    // -------------------------------------------------------------------------
    // Member summary (manager-scoped, cached per member+period)
    // -------------------------------------------------------------------------

    @Cacheable(value = "ai_summaries", key = "{'member', #teamId, #memberId, #from, #to}")
    public MetricsSummaryDto generateMemberSummary(User requestingUser, Long teamId, Long memberId,
                                                    LocalDate from, LocalDate to) {
        Team team = teamService.getById(teamId);

        assertManagerOrAdmin(requestingUser, team);

        User member = userService.getById(memberId);

        AggregatedMetricsContext ctx = contextBuilder.buildPersonalContext(member, from, to, null);
        String ctxJson = toJson(ctx);

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(from, to, null, ctxJson);

        log.info("Generating member AI summary: requesterId={}, teamId={}, memberId={}, from={}, to={}, model={}",
                requestingUser.getId(), teamId, memberId, from, to, model);

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt, true);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("Member AI summary generated: memberId={}, durationMs={}, responseLen={}", memberId, durationMs, raw.length());

        MetricsSummaryDto dto = parseSummary(raw, from, to, "PERSONAL", member.getUsername());
        persistenceService.savePersonal(member, dto);
        return dto;
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
                    { "kind": "risk",     "text": "...", "metric": "Churn Ratio", "explanation": "One sentence stating the most likely cause." },
                    { "kind": "note",     "text": "...", "metric": "Daily Commits" }
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
                3a. For every insight where the source metric had anomaly: true, add an "explanation"
                    field containing exactly one sentence stating the most likely cause, grounded in the
                    metric values (median, trendPct) provided. Omit "explanation" for non-anomalous insights.
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
                    { "kind": "risk",     "text": "...", "metric": "Churn Ratio", "explanation": "One sentence stating the most likely cause." },
                    { "kind": "note",     "text": "...", "metric": "Daily Commits" }
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
                3a. For every insight where the source metric had anomaly: true, add an "explanation"
                    field containing exactly one sentence stating the most likely cause, grounded in the
                    metric values (median, trendPct) provided. Omit "explanation" for non-anomalous insights.
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
            List<MetricsSummaryDto.InsightDto> insights = new ArrayList<>();
            JsonNode insightsNode = root.path("insights");
            if (insightsNode.isArray()) {
                for (JsonNode node : insightsNode) {
                    if (node.isTextual()) {
                        insights.add(MetricsSummaryDto.InsightDto.builder()
                                .kind("note").text(node.asText()).metric("").build());
                    } else if (node.isObject()) {
                        String explanation = node.path("explanation").isMissingNode() || node.path("explanation").isNull()
                                ? null
                                : node.path("explanation").asText(null);
                        insights.add(MetricsSummaryDto.InsightDto.builder()
                                .kind(node.path("kind").asText("note"))
                                .text(node.path("text").asText(""))
                                .metric(node.path("metric").asText(""))
                                .explanation(explanation)
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

    private void assertManagerOrAdmin(User requestingUser, Team team) {
        if (requestingUser.getRole() != Role.ADMIN
                && !team.getManager().getId().equals(requestingUser.getId())) {
            throw new ForbiddenException("Only the team manager or an admin can generate AI summaries for this team");
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize metrics context", e);
        }
    }
}
