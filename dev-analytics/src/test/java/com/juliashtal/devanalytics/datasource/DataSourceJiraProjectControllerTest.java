package com.juliashtal.devanalytics.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.datasource.controller.DataSourceJiraProjectController;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.jira.model.dto.AttachProjectRequest;
import com.juliashtal.devanalytics.jira.model.dto.DiscoveredProjectDto;
import com.juliashtal.devanalytics.jira.model.dto.JiraProjectResponseDto;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link DataSourceJiraProjectController}.
 *
 * <p>Attach mirrors the repo side's cross-datasource contract — 201 when the project becomes
 * canonical here, 200 when it already is elsewhere — and both are what the client branches on.</p>
 */
@WebMvcTest(DataSourceJiraProjectController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class DataSourceJiraProjectControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean JiraProjectService jiraProjectService;
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

    private static JiraProjectResponseDto project(long id, long dataSourceId) {
        return new JiraProjectResponseDto(id, dataSourceId, "https://jira.example.com",
                "PDA", "Analytics", null, true);
    }

    @Test
    void list_authenticated_scopesToTheCaller() throws Exception {
        when(jiraProjectService.listProjectsForDataSource(7L, 3L)).thenReturn(List.of(project(1L, 3L)));

        mockMvc.perform(get("/api/datasources/3/projects").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectKey").value("PDA"));

        verify(jiraProjectService).listProjectsForDataSource(7L, 3L);
    }

    @Test
    void attach_canonicalUnderThisDataSource_returns201WithLocation() throws Exception {
        AttachProjectRequest req = new AttachProjectRequest("PDA", "Analytics");
        when(jiraProjectService.attachProject(7L, 3L, "PDA", "Analytics")).thenReturn(project(1L, 3L));

        mockMvc.perform(post("/api/datasources/3/projects")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/datasources/3/projects/1"));
    }

    @Test
    void attach_canonicalElsewhere_returns200SoTheClientCanRefresh() throws Exception {
        AttachProjectRequest req = new AttachProjectRequest("PDA", "Analytics");
        when(jiraProjectService.attachProject(7L, 3L, "PDA", "Analytics")).thenReturn(project(1L, 9L));

        mockMvc.perform(post("/api/datasources/3/projects")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataSourceId").value(9));
    }

    @Test
    void attach_blankProjectKey_returns400() throws Exception {
        AttachProjectRequest req = new AttachProjectRequest("  ", "Analytics");

        mockMvc.perform(post("/api/datasources/3/projects")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detach_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/datasources/3/projects/1").with(user(principal(7L))))
                .andExpect(status().isNoContent());

        verify(jiraProjectService).detachProject(7L, 3L, 1L);
    }

    @Test
    void detach_notTheOwner_returns403() throws Exception {
        doThrow(new ForbiddenException("Not the datasource owner"))
                .when(jiraProjectService).detachProject(7L, 3L, 1L);

        mockMvc.perform(delete("/api/datasources/3/projects/1").with(user(principal(7L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void discoverProjects_authenticated_annotatesWhatIsAlreadyAttached() throws Exception {
        when(jiraProjectService.discoverProjectsFromJira(7L, 3L))
                .thenReturn(List.of(new DiscoveredProjectDto("PDA", "Analytics", true)));

        mockMvc.perform(get("/api/datasources/3/projects/discover-projects").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alreadyAttached").value(true));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/datasources/3/projects"))
                .andExpect(status().isUnauthorized());
    }
}
