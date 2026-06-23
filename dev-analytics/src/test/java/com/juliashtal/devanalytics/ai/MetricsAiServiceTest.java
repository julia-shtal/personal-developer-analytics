package com.juliashtal.devanalytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.juliashtal.devanalytics.ai.client.LlmClient;
import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MetricSummaryPersistenceService;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        // lenient: not every test exercises the personal/repo==null context path.
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(any(), any(), any(), any()))
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

    private static final String VALID_JSON = """
            {
              "headline": "Headline",
              "overview": "Overview",
              "insights": [],
              "recommendations": []
            }
            """;

    private User userWithRole(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private Team team(long id, User manager, User... members) {
        Team t = new Team();
        t.setId(id);
        t.setName("Team Alpha");
        t.setManager(manager);
        Set<User> memberSet = new HashSet<>();
        for (User member : members) {
            memberSet.add(member);
        }
        t.setMembers(memberSet);
        return t;
    }

    @Test
    void generateSummary_withRepoId_returnsRepositoryScopedSummary() {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(5L);
        repo.setName("dev-analytics");
        when(repoService.getById(5L)).thenReturn(repo);
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(metricSnapshotService.getMetricSnapshotsByUserAndMetricTypeAndRepositoryAndDateFromAndTo(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.complete(eq(MODEL), any(), userPromptCaptor.capture(), anyBoolean())).thenReturn(VALID_JSON);

        MetricsSummaryDto dto = service.generateSummary(user, from, to, 5L);

        assertThat(dto.getScope()).isEqualTo("REPOSITORY");
        assertThat(dto.getContextRepoName()).isEqualTo("dev-analytics");
        assertThat(userPromptCaptor.getValue()).contains("for repository dev-analytics");
        verify(persistenceService).savePersonal(user, dto);
    }

    @Test
    void generateTeamSummary_asManager_returnsTeamScopedSummary() {
        User manager = userWithRole(1L, Role.MANAGER);
        Team team = team(10L, manager, manager);
        when(teamService.getById(10L)).thenReturn(team);
        when(metricSnapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(llmClient.complete(eq(MODEL), any(), any(), anyBoolean())).thenReturn(VALID_JSON);

        MetricsSummaryDto dto = service.generateTeamSummary(manager, 10L, from, to);

        assertThat(dto.getScope()).isEqualTo("TEAM");
        assertThat(dto.getContextRepoName()).isEqualTo("Team Alpha");
        verify(persistenceService).saveTeam(team, dto);
    }

    @Test
    void generateTeamSummary_asAdminNonManager_returnsTeamScopedSummary() {
        User manager = userWithRole(1L, Role.MANAGER);
        User admin = userWithRole(2L, Role.ADMIN);
        Team team = team(10L, manager, manager);
        when(teamService.getById(10L)).thenReturn(team);
        when(metricSnapshotService.getMetricSnapshotsByUserAndTeamAndMetricTypeAndDateBetween(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(llmClient.complete(eq(MODEL), any(), any(), anyBoolean())).thenReturn(VALID_JSON);

        MetricsSummaryDto dto = service.generateTeamSummary(admin, 10L, from, to);

        assertThat(dto.getScope()).isEqualTo("TEAM");
        verify(persistenceService).saveTeam(team, dto);
    }

    @Test
    void generateTeamSummary_asNonManagerNonAdmin_throwsForbidden() {
        User manager = userWithRole(1L, Role.MANAGER);
        User developer = userWithRole(2L, Role.DEVELOPER);
        Team team = team(10L, manager, manager);
        when(teamService.getById(10L)).thenReturn(team);

        assertThatThrownBy(() -> service.generateTeamSummary(developer, 10L, from, to))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only the team manager or an admin can generate AI summaries for this team");

        verify(llmClient, never()).complete(any(), any(), any(), anyBoolean());
        verify(persistenceService, never()).saveTeam(any(), any());
    }

    @Test
    void generateMemberSummary_asManager_returnsPersonalSummaryForMember() {
        User manager = userWithRole(1L, Role.MANAGER);
        User member = new User();
        member.setId(2L);
        member.setUsername("alice");
        Team team = team(10L, manager, manager, member);
        when(teamService.getById(10L)).thenReturn(team);
        when(userService.getById(2L)).thenReturn(member);
        when(llmClient.complete(eq(MODEL), any(), any(), anyBoolean())).thenReturn(VALID_JSON);

        MetricsSummaryDto dto = service.generateMemberSummary(manager, 10L, 2L, from, to);

        assertThat(dto.getScope()).isEqualTo("PERSONAL");
        assertThat(dto.getContextRepoName()).isEqualTo("alice");
        verify(persistenceService).savePersonal(member, dto);
    }

    @Test
    void generateMemberSummary_asNonManagerNonAdmin_throwsForbidden() {
        User manager = userWithRole(1L, Role.MANAGER);
        User developer = userWithRole(2L, Role.DEVELOPER);
        Team team = team(10L, manager, manager);
        when(teamService.getById(10L)).thenReturn(team);

        assertThatThrownBy(() -> service.generateMemberSummary(developer, 10L, 3L, from, to))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only the team manager or an admin can generate AI summaries for this team");

        verify(llmClient, never()).complete(any(), any(), any(), anyBoolean());
        verify(persistenceService, never()).savePersonal(any(), any());
    }
}