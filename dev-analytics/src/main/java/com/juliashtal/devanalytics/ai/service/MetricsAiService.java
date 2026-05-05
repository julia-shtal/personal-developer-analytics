package com.juliashtal.devanalytics.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.ai.client.LlmClient;
import com.juliashtal.devanalytics.ai.model.MetricsContext;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private final MetricSnapshotService metricSnapshotService;
    private final RepoService repoService;
    private final TeamService teamService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.ollama.model:llama3}")
    private String model;

    // -------------------------------------------------------------------------
    // Personal / repository summary
    // -------------------------------------------------------------------------

    public MetricsSummaryDto generateSummary(User user, LocalDate from, LocalDate to, Long repoId) {
        GitRepositoryEntity repo = repoId != null ? repoService.getById(repoId) : null;

        MetricsContext ctx = buildMetricsContext(user, from, to, repo);
        String ctxJson = toJson(ctx);

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(from, to, repo, ctxJson);

        log.info("Generating AI summary: userId={}, scope={}, from={}, to={}, model={}, promptLen={}",
                user.getId(), repo != null ? "REPOSITORY" : "PERSONAL", from, to, model, userPrompt.length());

        long startedAt = System.currentTimeMillis();
        String raw = llmClient.complete(model, systemPrompt, userPrompt);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("AI summary generated: userId={}, durationMs={}, responseLen={}", user.getId(), durationMs, raw.length());

        return parseSummary(raw, from, to, repo != null ? "REPOSITORY" : "PERSONAL",
                repo != null ? repo.getName() : null);
    }

    // -------------------------------------------------------------------------
    // Team summary
    // -------------------------------------------------------------------------

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
        String raw = llmClient.complete(model, systemPrompt, userPrompt);
        long durationMs = System.currentTimeMillis() - startedAt;

        log.info("Team AI summary generated: teamId={}, durationMs={}, responseLen={}", teamId, durationMs, raw.length());

        return parseSummary(raw, from, to, "TEAM", team.getName());
    }

    // -------------------------------------------------------------------------
    // Context building — personal
    // -------------------------------------------------------------------------

    private MetricsContext buildMetricsContext(User user, LocalDate from, LocalDate to, GitRepositoryEntity repo) {
        Map<String, List<MetricsContext.DataPoint>> series = new LinkedHashMap<>();

        for (MetricType type : CONTEXT_METRIC_TYPES) {
            List<MetricSnapshot> snapshots;
            if (repo != null) {
                snapshots = metricSnapshotService
                        .getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(user, type, repo, from, to);
            } else {
                snapshots = metricSnapshotService
                        .getMetricSnapshotsByUserAndMetricTypeAndDateBetween(user, type, from, to);
            }

            if (!snapshots.isEmpty()) {
                List<MetricsContext.DataPoint> points = snapshots.stream()
                        .sorted(Comparator.comparing(MetricSnapshot::getDate))
                        .map(s -> new MetricsContext.DataPoint(s.getDate(), s.getValue()))
                        .toList();
                series.put(type.name(), points);
            }
        }

        MetricsContext ctx = new MetricsContext();
        ctx.setFrom(from);
        ctx.setTo(to);
        ctx.setRepoName(repo != null ? repo.getName() : null);
        ctx.setMetrics(series);
        return ctx;
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
                You are an assistant for a locally hosted developer analytics platform.

                You receive structured JSON with time series and aggregates for developer productivity \
                metrics across a selected period and scope. The available metrics may include daily commits, \
                PRs created and merged, issues created and closed, PR lead time, issue lead time, review \
                response time, code churn, and focus ratio. The data may be personal or repository-level.

                Your task is to produce a concise, developer-friendly summary that helps the user understand:
                - delivery flow and cycle efficiency,
                - review responsiveness,
                - multitasking versus focus,
                - bottlenecks, regressions, or anomalies,
                - concrete improvement opportunities.

                Use only the provided metrics. Do not speculate beyond the data.
                Be specific and reference concrete metric changes when possible.
                Avoid buzzwords, generic productivity language, and motivational phrasing.
                If data is incomplete or ambiguous, state that clearly.

                Respond with exactly these three sections and no other text:
                1. Overview: 1–2 short paragraphs.
                2. Insights: 5–8 bullet points.
                3. Recommendations: 3–5 bullet points.
                """;
    }

    private String buildUserPrompt(LocalDate from, LocalDate to, GitRepositoryEntity repo, String ctxJson) {
        String scopeInfo = repo != null ? " for repository " + repo.getName() : "";
        return """
                Analyze the following developer productivity metrics for the period %s to %s%s.

                The JSON contains time series for metrics such as daily commits, pull requests, issues, \
                lead times, review response time, churn, and focus ratio. Each entry in a series is a \
                {date, value} pair. Lead-time and aggregate metrics appear as a single entry per period.

                Rules:
                - Use only the provided data.
                - Do not speculate without evidence from the metrics.
                - Reference concrete values and trends when possible.
                - Prefer precise technical language over vague productivity language.
                - Keep the summary short and actionable.

                Output format:
                1. Overview: 1–2 short paragraphs.
                2. Insights: 5–8 bullet points.
                3. Recommendations: 3–5 bullet points.

                Metrics JSON:
                %s
                """.formatted(from, to, scopeInfo, ctxJson);
    }

    // -------------------------------------------------------------------------
    // Prompts — team
    // -------------------------------------------------------------------------

    private String buildTeamSystemPrompt() {
        return """
                You are an assistant for a locally hosted developer analytics platform.

                You receive structured JSON with per-member time series and aggregates for a software \
                development team across a selected period. The available metrics per member may include \
                daily commits, PRs created and merged, issues created and closed, PR lead time, issue \
                lead time, review response time, code churn, and focus ratio.

                Your task is to produce a concise, manager-friendly team summary that helps understand:
                - overall team delivery flow and output,
                - review responsiveness across members,
                - focus and multitasking patterns,
                - individual bottlenecks or risks (high churn, slow reviews, unreviewed merges),
                - concrete process improvement suggestions for the team.

                Use only the provided metrics. Do not speculate beyond the data.
                Reference concrete members and metric values when relevant.
                Avoid generic team language and motivational phrasing.
                If data is incomplete for some members, state that.

                Respond with exactly these three sections and no other text:
                1. Overview: 1–2 short paragraphs on the team's overall delivery and collaboration.
                2. Insights: 5–8 bullet points highlighting patterns, outliers, and risks across members.
                3. Recommendations: 3–5 bullet points for concrete team process improvements.
                """;
    }

    private String buildTeamUserPrompt(LocalDate from, LocalDate to, String teamName, String ctxJson) {
        return """
                Analyze the following team productivity metrics for team "%s" for the period %s to %s.

                The JSON contains per-member time series. Each member has a "metrics" map where \
                each key is a metric type and the value is a list of {date, value} data points. \
                Aggregate metrics (lead times, ratios) appear as a single entry per period.

                Rules:
                - Use only the provided data.
                - Reference member usernames and specific metric values when possible.
                - Identify cross-member patterns (e.g., who has the slowest reviews, highest churn).
                - Prefer precise technical language.
                - Keep the summary short and actionable.

                Output format:
                1. Overview: 1–2 short paragraphs.
                2. Insights: 5–8 bullet points.
                3. Recommendations: 3–5 bullet points.

                Team Metrics JSON:
                %s
                """.formatted(teamName, from, to, ctxJson);
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private MetricsSummaryDto parseSummary(String raw, LocalDate from, LocalDate to,
                                           String scope, String scopeName) {
        String text = raw.replace("\r\n", "\n").replace("\r", "\n");

        String overview = extractSection(text, "Overview", new String[]{"Insights", "Recommendations"});
        String insightsRaw = extractSection(text, "Insights", new String[]{"Recommendations"});
        String recommendationsRaw = extractSection(text, "Recommendations", new String[]{});

        List<String> insights = parseBullets(insightsRaw);
        List<String> recommendations = parseBullets(recommendationsRaw);

        if (overview.isBlank() && insights.isEmpty() && recommendations.isEmpty()) {
            log.warn("Could not parse structured sections from model output; using raw text as overview");
            overview = raw;
        }

        return MetricsSummaryDto.builder()
                .from(from)
                .to(to)
                .scope(scope)
                .repoName(scopeName)
                .overview(overview.strip())
                .insights(insights)
                .recommendations(recommendations)
                .rawModelOutput(raw)
                .modelName(model)
                .build();
    }

    private String extractSection(String text, String sectionName, String[] nextSections) {
        Pattern headerPattern = buildHeaderPattern(sectionName);
        Matcher headerMatcher = headerPattern.matcher(text);
        if (!headerMatcher.find()) {
            return "";
        }
        int start = headerMatcher.end();

        int end = text.length();
        for (String next : nextSections) {
            Pattern nextPattern = buildHeaderPattern(next);
            Matcher nextMatcher = nextPattern.matcher(text);
            nextMatcher.region(start, text.length());
            if (nextMatcher.find()) {
                end = Math.min(end, nextMatcher.start());
            }
        }
        return text.substring(start, end).strip();
    }

    private Pattern buildHeaderPattern(String sectionName) {
        String regex = "(?im)^[#*\\d.\\s]*\\*{0,2}" + Pattern.quote(sectionName) + "\\*{0,2}:?\\s*$";
        return Pattern.compile(regex);
    }

    private List<String> parseBullets(String text) {
        if (text == null || text.isBlank()) return new ArrayList<>();
        return Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> line.matches("^[-*•]\\s+.+") || line.matches("^\\d+[.):]\\s+.+"))
                .map(line -> line.replaceFirst("^[-*•]\\s+|^\\d+[.):] ?", "").trim())
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
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
