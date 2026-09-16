package com.juliashtal.devanalytics.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.github.commit.GitHubCommitCollector;
import com.juliashtal.devanalytics.github.controller.GitHubController;
import com.juliashtal.devanalytics.github.model.dto.RegisterGitHubRepoRequest;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link GitHubController}.
 *
 * <p>Registration reads the owner from the security context via {@code SecurityUtils}, so the
 * principal is a real {@link CustomUserDetails} — a plain mock user would leave the controller
 * with no id to attribute the repository to.</p>
 */
@WebMvcTest(GitHubController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class GitHubControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean GitHubRepositoryService gitHubRepositoryService;
    @MockBean GitHubCommitCollector commitCollector;
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

    private static GitRepositoryEntity repo(long id, String name) {
        GitRepositoryEntity e = new GitRepositoryEntity();
        e.setId(id);
        e.setName(name);
        return e;
    }

    @Test
    void registerRepo_authenticated_attributesTheRepoToTheCallerNotTheBody() throws Exception {
        RegisterGitHubRepoRequest req = new RegisterGitHubRepoRequest();
        req.setDataSourceId(3L);
        req.setFullName("owner/repo");
        when(gitHubRepositoryService.registerGitHubRepo(eq(7L), eq(3L), eq("owner/repo")))
                .thenReturn(repo(11L, "repo"));

        mockMvc.perform(post("/api/github/repos")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.name").value("repo"));

        verify(gitHubRepositoryService).registerGitHubRepo(7L, 3L, "owner/repo");
    }

    @Test
    void collect_authenticated_reportsTheSavedCount() throws Exception {
        when(commitCollector.collectForRepository(eq(11L), isNull())).thenReturn(42);

        mockMvc.perform(post("/api/github/repos/11/collect").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(content().string("Collected 42 commits from GitHub"));
    }

    @Test
    void registerRepo_unauthenticated_returns401() throws Exception {
        RegisterGitHubRepoRequest req = new RegisterGitHubRepoRequest();
        req.setDataSourceId(3L);
        req.setFullName("owner/repo");

        mockMvc.perform(post("/api/github/repos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(gitHubRepositoryService, org.mockito.Mockito.never())
                .registerGitHubRepo(any(), any(), any());
    }

    @Test
    void collect_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/github/repos/11/collect"))
                .andExpect(status().isUnauthorized());
    }
}
