package com.juliashtal.devanalytics.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.jira.controller.JiraProjectController;
import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.dto.CreateTrackedJiraProjectRequest;
import com.juliashtal.devanalytics.jira.service.JiraProjectMappingService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link JiraProjectController}.
 *
 * <p>Routes taking a {@code dataSourceId} resolve it for the caller and reject a non-JIRA type
 * before doing anything else; the subscription routes pass the caller's own id, never one from
 * the request.</p>
 */
@WebMvcTest(JiraProjectController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class JiraProjectControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean JiraProjectService jiraProjectService;
    @MockBean JiraProjectMappingService jiraProjectMappingService;
    @MockBean DataSourceService dataSourceService;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Both a controller collaborator and the bean ActivityInterceptor needs
    @MockBean UserService userService;

    private static CustomUserDetails principal(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("alice");
        u.setRole(Role.DEVELOPER);
        return new CustomUserDetails(u);
    }

    private static DataSourceConfig jiraDataSource(long id) {
        DataSourceConfig cfg = new DataSourceConfig();
        cfg.setId(id);
        cfg.setType(DataSourceType.JIRA);
        cfg.setBaseUrl("https://jira.example.com");
        return cfg;
    }

    private static JiraProjectEntity project(long id, DataSourceConfig ds) {
        JiraProjectEntity e = new JiraProjectEntity();
        e.setId(id);
        e.setDataSource(ds);
        e.setProjectKey("PDA");
        e.setProjectName("Analytics");
        return e;
    }

    @Test
    void listTracked_jiraDataSource_marksSubscriptionPerProject() throws Exception {
        DataSourceConfig ds = jiraDataSource(3L);
        JiraProjectEntity p = project(1L, ds);
        User caller = new User();
        caller.setId(7L);
        when(dataSourceService.getForUser(7L, 3L)).thenReturn(ds);
        when(userService.getReferenceById(7L)).thenReturn(caller);
        when(jiraProjectService.listTrackedProjects(ds)).thenReturn(List.of(p));
        when(jiraProjectService.isSubscribed(caller, p)).thenReturn(true);

        mockMvc.perform(get("/api/jira-projects").param("dataSourceId", "3").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectKey").value("PDA"))
                .andExpect(jsonPath("$[0].subscribed").value(true));
    }

    @Test
    void listTracked_githubDataSource_returns400AndNeverListsProjects() throws Exception {
        DataSourceConfig github = new DataSourceConfig();
        github.setId(3L);
        github.setType(DataSourceType.GITHUB);
        when(dataSourceService.getForUser(7L, 3L)).thenReturn(github);

        mockMvc.perform(get("/api/jira-projects").param("dataSourceId", "3").with(user(principal(7L))))
                .andExpect(status().isBadRequest());

        verify(jiraProjectService, never()).listTrackedProjects(github);
    }

    @Test
    void listAvailable_jiraDataSource_returnsWhatTheApiOffers() throws Exception {
        DataSourceConfig ds = jiraDataSource(3L);
        when(dataSourceService.getForUser(7L, 3L)).thenReturn(ds);
        when(jiraProjectService.listProjects(ds))
                .thenReturn(List.of(new JiraProjectService.JiraProjectDto("PDA", "Analytics", "10001")));

        mockMvc.perform(get("/api/jira-projects/available")
                        .param("dataSourceId", "3").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("PDA"));
    }

    @Test
    void addProject_jiraDataSource_returns201() throws Exception {
        DataSourceConfig ds = jiraDataSource(3L);
        CreateTrackedJiraProjectRequest req = new CreateTrackedJiraProjectRequest();
        req.setDataSourceId(3L);
        req.setProjectKey("PDA");
        req.setProjectName("Analytics");
        when(dataSourceService.getForUser(7L, 3L)).thenReturn(ds);
        when(jiraProjectService.addProject(ds, "PDA", "Analytics")).thenReturn(project(1L, ds));

        mockMvc.perform(post("/api/jira-projects")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectKey").value("PDA"))
                .andExpect(jsonPath("$.subscribed").value(false));
    }

    @Test
    void addProject_blankProjectKey_returns400() throws Exception {
        CreateTrackedJiraProjectRequest req = new CreateTrackedJiraProjectRequest();
        req.setDataSourceId(3L);
        req.setProjectKey("");

        mockMvc.perform(post("/api/jira-projects")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteProject_authenticated_passesTheCallersId() throws Exception {
        mockMvc.perform(delete("/api/jira-projects/1").with(user(principal(7L))))
                .andExpect(status().isNoContent());

        verify(jiraProjectService).deleteProject(1L, 7L);
    }

    @Test
    void subscribe_authenticated_subscribesTheCallerNotARequestId() throws Exception {
        mockMvc.perform(post("/api/jira-projects/1/subscribe").with(user(principal(7L))))
                .andExpect(status().isOk());

        verify(jiraProjectService).subscribeUser(1L, 7L);
    }

    @Test
    void unsubscribe_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/jira-projects/1/subscribe").with(user(principal(7L))))
                .andExpect(status().isNoContent());

        verify(jiraProjectService).unsubscribeUser(1L, 7L);
    }

    @Test
    void listLinkedRepos_authenticated_scopesToTheCaller() throws Exception {
        when(jiraProjectMappingService.listMappings(1L, 7L)).thenReturn(List.of(
                new RepoDto(11L, "repo", "owner/repo", null, 3L, true,
                        "https://github.com/owner/repo", false, null, null)));

        mockMvc.perform(get("/api/jira-projects/1/repositories").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repoFullName").value("owner/repo"));
    }

    @Test
    void linkRepo_authenticated_returns200() throws Exception {
        mockMvc.perform(post("/api/jira-projects/1/repositories/11").with(user(principal(7L))))
                .andExpect(status().isOk());

        verify(jiraProjectMappingService).link(7L, 1L, 11L);
    }

    @Test
    void unlinkRepo_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/jira-projects/1/repositories/11").with(user(principal(7L))))
                .andExpect(status().isNoContent());

        verify(jiraProjectMappingService).unlink(7L, 1L, 11L);
    }

    @Test
    void listTracked_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/jira-projects").param("dataSourceId", "3"))
                .andExpect(status().isUnauthorized());
    }
}
