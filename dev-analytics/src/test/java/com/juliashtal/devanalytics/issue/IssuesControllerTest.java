package com.juliashtal.devanalytics.issue;

import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.github.issue.GitHubIssuesCollector;
import com.juliashtal.devanalytics.issue.controller.IssuesController;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.issue.service.IssueService;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.service.JiraCollector;
import com.juliashtal.devanalytics.jira.service.JiraProjectService;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link IssuesController}.
 *
 * <p>Every route resolves its Jira project, repository or datasource through an ownership check
 * before reading issues, so the tests that matter here are the ones where that check rejects: a
 * datasource belonging to someone else, or one of the wrong type, must not reach the collector.</p>
 */
@WebMvcTest(IssuesController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class IssuesControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean IssueService issueService;
    @MockBean JiraCollector jiraCollector;
    @MockBean JiraProjectService jiraProjectService;
    @MockBean GitHubIssuesCollector gitHubIssuesCollector;
    @MockBean DataSourceService dataSourceService;
    @MockBean RepoService repoService;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor (HandlerInterceptor picked up by @WebMvcTest)
    @MockBean UserService userService;

    private static CustomUserDetails principal(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("alice");
        u.setRole(Role.DEVELOPER);
        return new CustomUserDetails(u);
    }

    private static DataSourceConfig dataSource(long id, long ownerId, DataSourceType type) {
        User owner = new User();
        owner.setId(ownerId);
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(id);
        cfg.setUser(owner);
        cfg.setType(type);
        return cfg;
    }

    private static IssueEntity issue(long id, String key, String title) {
        IssueEntity e = new IssueEntity();
        e.setId(id);
        e.setSourceIssueKey(key);
        e.setTitle(title);
        e.setState("open");
        return e;
    }

    @Test
    void getIssueCount_authenticated_returnsOpenAndClosed() throws Exception {
        when(issueService.countByRepository(11L)).thenReturn(Map.of("open", 3L, "closed", 7L));

        mockMvc.perform(get("/api/issues/count").param("repoId", "11").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(3))
                .andExpect(jsonPath("$.closed").value(7));
    }

    @Test
    void getJiraProjectIssueCount_authenticated_checksOwnershipFirst() throws Exception {
        when(jiraProjectService.getProjectForUser(5L, 7L)).thenReturn(new JiraProjectEntity());
        when(issueService.countByJiraProject(5L)).thenReturn(Map.of("open", 1L));

        mockMvc.perform(get("/api/issues/jira/5/count").with(user(principal(7L))))
                .andExpect(status().isOk());

        verify(jiraProjectService).getProjectForUser(5L, 7L);
    }

    @Test
    void collectJira_ownedProject_reportsTheCollectedCount() throws Exception {
        JiraProjectEntity project = new JiraProjectEntity();
        when(jiraProjectService.getProjectForUser(5L, 7L)).thenReturn(project);
        when(jiraCollector.collectIssues(project)).thenReturn(14);

        mockMvc.perform(post("/api/issues/jira/5/collect").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(content().string("Collected/updated 14 Jira issues"));
    }

    @Test
    void collectGitHub_ownedGithubDataSource_reportsTheCollectedCount() throws Exception {
        when(dataSourceService.getDataSource(3L)).thenReturn(dataSource(3L, 7L, DataSourceType.GITHUB));
        when(gitHubIssuesCollector.collectIssuesForRepo(any(DataSourceConfig.class), eq("owner/repo")))
                .thenReturn(5);

        mockMvc.perform(post("/api/issues/github/3/repos/owner/repo/collect").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(content().string("Collected/updated 5 GitHub issues"));
    }

    @Test
    void collectGitHub_someoneElsesDataSource_returns400AndNeverCollects() throws Exception {
        when(dataSourceService.getDataSource(3L)).thenReturn(dataSource(3L, 99L, DataSourceType.GITHUB));

        mockMvc.perform(post("/api/issues/github/3/repos/owner/repo/collect").with(user(principal(7L))))
                .andExpect(status().isBadRequest());

        verify(gitHubIssuesCollector, never()).collectIssuesForRepo(any(DataSourceConfig.class), anyString());
    }

    @Test
    void collectGitHub_jiraDataSource_returns400AndNeverCollects() throws Exception {
        when(dataSourceService.getDataSource(3L)).thenReturn(dataSource(3L, 7L, DataSourceType.JIRA));

        mockMvc.perform(post("/api/issues/github/3/repos/owner/repo/collect").with(user(principal(7L))))
                .andExpect(status().isBadRequest());

        verify(gitHubIssuesCollector, never()).collectIssuesForRepo(any(DataSourceConfig.class), anyString());
    }

    @Test
    void listJiraIssues_defaultPaging_requestsFirstPageOf50() throws Exception {
        JiraProjectEntity project = new JiraProjectEntity();
        when(jiraProjectService.getProjectForUser(5L, 7L)).thenReturn(project);
        when(issueService.getByJiraProject(eq(project), any()))
                .thenReturn(new PageImpl<>(List.of(issue(1L, "PDA-1", "Fix chart"))));

        mockMvc.perform(get("/api/issues/jira/5").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sourceIssueKey").value("PDA-1"));

        verify(issueService).getByJiraProject(project, PageRequest.of(0, 50));
    }

    @Test
    void listGitHubIssues_explicitPaging_resolvesTheRepoThroughTheAccessCheck() throws Exception {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        when(repoService.getAccessibleRepo(7L, 11L)).thenReturn(repo);
        when(issueService.getByRepository(eq(repo), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/issues/github/11")
                        .with(user(principal(7L)))
                        .param("page", "2").param("size", "10"))
                .andExpect(status().isOk());

        verify(repoService).getAccessibleRepo(7L, 11L);
        verify(issueService).getByRepository(repo, PageRequest.of(2, 10));
    }

    @Test
    void getIssueCount_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/issues/count").param("repoId", "11"))
                .andExpect(status().isUnauthorized());
    }
}
