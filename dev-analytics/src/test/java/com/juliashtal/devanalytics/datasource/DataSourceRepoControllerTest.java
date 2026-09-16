package com.juliashtal.devanalytics.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.datasource.controller.DataSourceRepoController;
import com.juliashtal.devanalytics.datasource.model.dto.AttachRepoRequest;
import com.juliashtal.devanalytics.datasource.service.DataSourceService;
import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.github.model.dto.DiscoveredRepoDto;
import com.juliashtal.devanalytics.github.model.dto.DiscoveryResult;
import com.juliashtal.devanalytics.github.repo.GitHubRepositoryService;
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
 * Controller slice tests for {@link DataSourceRepoController}.
 *
 * <p>Attach is idempotent across datasources and says so in its status code: 201 when the repo
 * becomes canonical here, 200 when it is canonical elsewhere and the caller was subscribed
 * instead. Clients branch on that, so both paths are pinned.</p>
 */
@WebMvcTest(DataSourceRepoController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class DataSourceRepoControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean DataSourceService dataSourceService;
    @MockBean GitHubRepositoryService gitHubRepositoryService;
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

    private static RepoDto repo(long id, long dataSourceId) {
        return new RepoDto(id, "repo", "owner/repo", null, dataSourceId, true,
                "https://github.com/owner/repo", false, null, null);
    }

    @Test
    void list_authenticated_scopesToTheCaller() throws Exception {
        when(dataSourceService.listReposForDataSource(7L, 3L)).thenReturn(List.of(repo(1L, 3L)));

        mockMvc.perform(get("/api/datasources/3/repos").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repoFullName").value("owner/repo"));

        verify(dataSourceService).listReposForDataSource(7L, 3L);
    }

    @Test
    void attach_canonicalUnderThisDataSource_returns201WithLocation() throws Exception {
        AttachRepoRequest req = new AttachRepoRequest("owner/repo", false);
        when(dataSourceService.attachRepo(7L, 3L, "owner/repo", false)).thenReturn(repo(1L, 3L));

        mockMvc.perform(post("/api/datasources/3/repos")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/datasources/3/repos/1"));
    }

    @Test
    void attach_canonicalElsewhere_returns200SoTheClientCanRefresh() throws Exception {
        AttachRepoRequest req = new AttachRepoRequest("owner/repo", false);
        when(dataSourceService.attachRepo(7L, 3L, "owner/repo", false)).thenReturn(repo(1L, 9L));

        mockMvc.perform(post("/api/datasources/3/repos")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataSourceId").value(9));
    }

    @Test
    void attach_blankRepoName_returns400() throws Exception {
        AttachRepoRequest req = new AttachRepoRequest("  ", false);

        mockMvc.perform(post("/api/datasources/3/repos")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detach_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/datasources/3/repos/1").with(user(principal(7L))))
                .andExpect(status().isNoContent());

        verify(dataSourceService).detachRepo(7L, 3L, 1L);
    }

    @Test
    void detach_repoStillSubscribed_returns409() throws Exception {
        doThrow(new ConflictException("Repo still has subscriptions"))
                .when(dataSourceService).detachRepo(7L, 3L, 1L);

        mockMvc.perform(delete("/api/datasources/3/repos/1").with(user(principal(7L))))
                .andExpect(status().isConflict());
    }

    @Test
    void discoverRepos_truncatedResult_saysSoInTheHeader() throws Exception {
        when(gitHubRepositoryService.discoverRepos(7L, 3L)).thenReturn(new DiscoveryResult(
                List.of(new DiscoveredRepoDto("owner/repo", false, "main", true)), true));

        mockMvc.perform(get("/api/datasources/3/repos/discover-repos").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Discovery-Truncated", "true"))
                .andExpect(jsonPath("$[0].alreadyAttached").value(true));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/datasources/3/repos"))
                .andExpect(status().isUnauthorized());
    }
}
