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
import com.juliashtal.devanalytics.metrics.model.MetricType;
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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates AI metric-summary generation: builds context, prompts the LLM, and parses the JSON response.
 */
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
    private final PromptVersionProvider promptVersionProvider;
    private final SummaryValidator summaryValidator;

    @Value("${ai.ollama.model:llama3}")
    private String model;

    private static final String NO_DATA_HEADLINE = "No data for this period";
    private static final String NO_DATA_OVERVIEW =
            "No attributable activity was recorded for this period, so no summary was generated.";

    // -------------------------------------------------------------------------
    // Personal / repository summary
    // -------------------------------------------------------------------------

    @Cacheable(value = "ai_summaries",
            key = "{#user.id, #from, #to, #repoId, @promptVersionProvider.hashFor('PERSONAL')}")
    public MetricsSummaryDto generateSummary(User user, LocalDate from, LocalDate to, Long repoId) {
        // Entitlement check, not a lookup: repoId comes straight from the request.
        GitRepositoryEntity repo = repoId != null
                ? repoService.getAccessibleRepo(user.getId(), repoId)
                : null;
        String scope = repo != null ? "REPOSITORY" : "PERSONAL";
        String scopeName = repo != null ? repo.getName() : null;

        AggregatedMetricsContext ctx = contextBuilder.buildPersonalContext(user, from, to, repo);

        if (ctx.getMetrics().isEmpty()) {
            log.info("No metrics in context, skipping LLM: userId={}, scope={}, from={}, to={}",
                    user.getId(), scope, from, to);
            MetricsSummaryDto dto = noDataSummary(from, to, scope, scopeName);
            persistenceService.savePersonal(user, dto);
            return dto;
        }

        String ctxJson = toJson(ctx);

        String systemPrompt = personalSystemPrompt(ctx);
        String userPrompt = buildUserPrompt(from, to, repo, ctxJson);

        log.info("Generating AI summary: userId={}, scope={}, from={}, to={}, model={}, promptLen={}",
                user.getId(), scope, from, to, model, userPrompt.length());

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt, true);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("AI summary generated: userId={}, durationMs={}, responseLen={}", user.getId(), durationMs, raw.length());

        MetricsSummaryDto dto = parseSummary(raw, from, to, scope, scopeName,
                metricAliases(ctx.getMetrics().keySet()), anomalousMetricNames(ctx.getMetrics()),
                groundedNumberTokens(ctx.getMetrics()), trendPctByMetricName(ctx.getMetrics()));
        persistenceService.savePersonal(user, dto);
        return dto;
    }

    // -------------------------------------------------------------------------
    // Team summary
    // -------------------------------------------------------------------------

    @Cacheable(value = "ai_summaries",
            key = "{'team', #teamId, #from, #to, @promptVersionProvider.hashFor('TEAM')}")
    public MetricsSummaryDto generateTeamSummary(User requestingUser, Long teamId, LocalDate from, LocalDate to) {
        Team team = teamService.getById(teamId);

        assertManagerOrAdmin(requestingUser, team);

        TeamMetricsContext ctx = contextBuilder.buildTeamContext(team, from, to);

        if (teamContextHasNoData(ctx)) {
            log.info("No metrics in team context, skipping LLM: teamId={}, from={}, to={}", teamId, from, to);
            MetricsSummaryDto dto = noDataSummary(from, to, "TEAM", team.getName());
            persistenceService.saveTeam(team, dto);
            return dto;
        }

        String ctxJson = toJson(ctx);

        String systemPrompt = SystemPrompts.TEAM;
        String userPrompt = buildTeamUserPrompt(from, to, team.getName(), ctxJson);

        log.info("Generating team AI summary: teamId={}, teamName={}, members={}, from={}, to={}, model={}",
                teamId, team.getName(), team.getMembers().size(), from, to, model);

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt, true);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("Team AI summary generated: teamId={}, durationMs={}, responseLen={}", teamId, durationMs, raw.length());

        MetricsSummaryDto dto = parseSummary(raw, from, to, "TEAM", team.getName(),
                metricAliases(teamMetricKeys(ctx)), Set.of(), groundedNumberTokensForTeam(ctx), Map.of());
        persistenceService.saveTeam(team, dto);
        return dto;
    }

    // -------------------------------------------------------------------------
    // Member summary (manager-scoped, cached per member+period)
    // -------------------------------------------------------------------------

    @Cacheable(value = "ai_summaries",
            key = "{'member', #teamId, #memberId, #from, #to, @promptVersionProvider.hashFor('PERSONAL')}")
    public MetricsSummaryDto generateMemberSummary(User requestingUser, Long teamId, Long memberId,
                                                    LocalDate from, LocalDate to) {
        Team team = teamService.getById(teamId);

        assertManagerOrAdmin(requestingUser, team);

        User member = userService.getById(memberId);

        AggregatedMetricsContext ctx = contextBuilder.buildPersonalContext(member, from, to, null);

        if (ctx.getMetrics().isEmpty()) {
            log.info("No metrics in context, skipping LLM: memberId={}, teamId={}, from={}, to={}",
                    memberId, teamId, from, to);
            MetricsSummaryDto dto = noDataSummary(from, to, "PERSONAL", member.getUsername());
            persistenceService.savePersonal(member, dto);
            return dto;
        }

        String ctxJson = toJson(ctx);

        String systemPrompt = personalSystemPrompt(ctx);
        String userPrompt = buildUserPrompt(from, to, null, ctxJson);

        log.info("Generating member AI summary: requesterId={}, teamId={}, memberId={}, from={}, to={}, model={}",
                requestingUser.getId(), teamId, memberId, from, to, model);

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt, true);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("Member AI summary generated: memberId={}, durationMs={}, responseLen={}", memberId, durationMs, raw.length());

        MetricsSummaryDto dto = parseSummary(raw, from, to, "PERSONAL", member.getUsername(),
                metricAliases(ctx.getMetrics().keySet()), anomalousMetricNames(ctx.getMetrics()),
                groundedNumberTokens(ctx.getMetrics()), trendPctByMetricName(ctx.getMetrics()));
        persistenceService.savePersonal(member, dto);
        return dto;
    }

    // -------------------------------------------------------------------------
    // Prompts — personal / repository
    // -------------------------------------------------------------------------

    /** Appends the goal-coaching block only when the context actually carries an active goal. */
    private String personalSystemPrompt(AggregatedMetricsContext ctx) {
        return ctx.getActiveGoals().isEmpty()
                ? SystemPrompts.PERSONAL
                : SystemPrompts.PERSONAL + SystemPrompts.GOAL_COACHING_BLOCK;
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

    private String buildTeamUserPrompt(LocalDate from, LocalDate to, String teamName, String ctxJson) {
        return """
                Analyze the following team productivity metrics for team "%s" for the period %s to %s.
                Return ONLY the JSON object as specified. No markdown, no extra text.

                Team Metrics JSON:
                %s
                """.formatted(teamName, from, to, ctxJson);
    }

    // -------------------------------------------------------------------------
    // Empty-context guard
    // -------------------------------------------------------------------------

    /** True when no team member has any attributable metric in the period, so a summary would have nothing to ground itself in. */
    private boolean teamContextHasNoData(TeamMetricsContext ctx) {
        return ctx.getMembers() == null
                || ctx.getMembers().stream().allMatch(m -> m.getMetrics() == null || m.getMetrics().isEmpty());
    }

    /** Placeholder summary for a period with no attributable metrics, skipping the LLM call entirely. */
    private MetricsSummaryDto noDataSummary(LocalDate from, LocalDate to, String scope, String scopeName) {
        return MetricsSummaryDto.builder()
                .from(from)
                .to(to)
                .scope(scope)
                .contextRepoName(scopeName)
                .headline(NO_DATA_HEADLINE)
                .overview(NO_DATA_OVERVIEW)
                .insights(List.of())
                .recommendations(List.of())
                .rawModelOutput(null)
                .modelName(model)
                .promptVersion(promptVersionProvider.hashFor(scope))
                .runtimeVersion(llmClient.runtimeVersion())
                .build();
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private MetricsSummaryDto parseSummary(String raw, LocalDate from, LocalDate to,
                                           String scope, String scopeName,
                                           Map<String, String> metricAliases, Set<String> anomalousMetricNames,
                                           Set<String> groundedNumbers, Map<String, Double> trendPctByMetric) {
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

            SummaryValidator.Result validated =
                    summaryValidator.validate(insights, metricAliases, anomalousMetricNames, groundedNumbers,
                            trendPctByMetric);

            return MetricsSummaryDto.builder()
                    .from(from)
                    .to(to)
                    .scope(scope)
                    .contextRepoName(scopeName)
                    .headline(headline)
                    .overview(overview)
                    .insights(validated.insights())
                    .recommendations(recommendations)
                    .rawModelOutput(raw)
                    .modelName(model)
                    .promptVersion(promptVersionProvider.hashFor(scope))
                    .runtimeVersion(llmClient.runtimeVersion())
                    .validationReport(toJson(validated.report()))
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
                    .promptVersion(promptVersionProvider.hashFor(scope))
                    .runtimeVersion(llmClient.runtimeVersion())
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Metric-name lookups for validation
    // -------------------------------------------------------------------------

    private Optional<String> humanName(String contextKey) {
        try {
            return Optional.ofNullable(MetricDisplayNames.BY_TYPE.get(MetricType.valueOf(contextKey)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Every spelling the model may legitimately use for a context metric — its human label and
     * its enum key — mapped to the canonical human label. A sparse period that leaves the model
     * echoing the raw enum key back (e.g. {@code FOCUS_RATIO_DAYS_TASKS}) must not lose the
     * insight to a naming slip the prompt already permits either form for.
     */
    private Map<String, String> metricAliases(Set<String> contextKeys) {
        Map<String, String> aliases = new HashMap<>();
        for (String key : contextKeys) {
            humanName(key).ifPresent(human -> {
                aliases.put(human, human);
                aliases.put(key, human);
            });
        }
        return aliases;
    }

    private Set<String> anomalousMetricNames(Map<String, AggregatedMetricsContext.MetricAggregate> metrics) {
        Set<String> names = new HashSet<>();
        metrics.forEach((key, aggregate) -> {
            if (aggregate.isAnomaly()) {
                humanName(key).ifPresent(names::add);
            }
        });
        return names;
    }

    /** Trend of each context metric keyed by canonical human label, the key the validator resolves aliases to. */
    private Map<String, Double> trendPctByMetricName(Map<String, AggregatedMetricsContext.MetricAggregate> metrics) {
        Map<String, Double> trends = new HashMap<>();
        metrics.forEach((key, aggregate) -> humanName(key).ifPresent(name -> trends.put(name, aggregate.getTrendPct())));
        return trends;
    }

    /** TEAM context carries no anomaly flag per metric, so only metric presence is checked at this scope. */
    private Set<String> teamMetricKeys(TeamMetricsContext ctx) {
        Set<String> keys = new HashSet<>();
        for (TeamMetricsContext.MemberMetrics member : ctx.getMembers()) {
            keys.addAll(member.getMetrics().keySet());
        }
        return keys;
    }

    // -------------------------------------------------------------------------
    // Number groundedness (flag only; see SummaryValidator)
    // -------------------------------------------------------------------------

    /** Every min/max/median/total/trendPct value actually supplied, exactly as formatted for the prompt. */
    private Set<String> groundedNumberTokens(Map<String, AggregatedMetricsContext.MetricAggregate> metrics) {
        Set<String> tokens = new HashSet<>();
        for (AggregatedMetricsContext.MetricAggregate agg : metrics.values()) {
            tokens.add(agg.getMin());
            tokens.add(agg.getMax());
            tokens.add(agg.getMedian());
            if (agg.getTotal() != 0) {
                tokens.add(String.valueOf(agg.getTotal()));
            }
            tokens.addAll(trendPctTokens(agg.getTrendPct()));
        }
        return tokens;
    }

    private Set<String> groundedNumberTokensForTeam(TeamMetricsContext ctx) {
        DecimalFormat df = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US));
        Set<String> tokens = new HashSet<>();
        for (TeamMetricsContext.MemberMetrics member : ctx.getMembers()) {
            member.getMetrics().values().forEach(v -> tokens.add(df.format(v)));
        }
        return tokens;
    }

    /**
     * Both the one-decimal form the prompt asks for and the bare-integer form a model tends to
     * fall back to for a whole-number trend (e.g. {@code 0} alongside {@code 0.0}).
     */
    private Set<String> trendPctTokens(double trendPct) {
        String oneDecimal = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US)).format(trendPct);
        String integer = String.valueOf(Math.round(trendPct));
        return Set.of(oneDecimal, integer);
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
