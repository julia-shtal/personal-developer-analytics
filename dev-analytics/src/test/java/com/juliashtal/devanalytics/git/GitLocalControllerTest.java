package com.juliashtal.devanalytics.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.git.controller.GitLocalController;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.dto.RegisterLocalRepoRequest;
import com.juliashtal.devanalytics.git.service.GitLocalCollector;
import com.juliashtal.devanalytics.git.service.GitRepositoryService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link GitLocalController}.
 *
 * <p>Every route scopes to the caller's id from the security context, so the principal is a real
 * {@link CustomUserDetails}; the ownership argument reaching the service is what these assert.</p>
 */
@WebMvcTest(GitLocalController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class GitLocalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean GitRepositoryService gitRepositoryService;
    @MockBean GitLocalCollector gitLocalCollector;
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
        e.setLocalPath("C:/repos/" + name);
        return e;
    }

    private static GitCommitEntity commit(long id, String hash) {
        GitCommitEntity e = new GitCommitEntity();
        e.setId(id);
        e.setHash(hash);
        e.setAuthorName("alice");
        e.setAuthorEmail("alice@example.com");
        e.setAuthorDate(Instant.parse("2026-09-01T09:00:00Z"));
        e.setMessage("Add calculator");
        return e;
    }

    @Test
    void registerLocalRepo_validRequest_registersUnderTheCaller() throws Exception {
        RegisterLocalRepoRequest req = new RegisterLocalRepoRequest();
        req.setDataSourceId(3L);
        req.setName("analytics");
        req.setLocalPath("C:/repos/analytics");
        when(gitRepositoryService.registerLocalRepo(eq(7L), any())).thenReturn(repo(11L, "analytics"));

        mockMvc.perform(post("/api/git/local/repos")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.name").value("analytics"));
    }

    @Test
    void registerLocalRepo_blankName_returns400() throws Exception {
        RegisterLocalRepoRequest req = new RegisterLocalRepoRequest();
        req.setDataSourceId(3L);
        req.setName("");
        req.setLocalPath("C:/repos/analytics");

        mockMvc.perform(post("/api/git/local/repos")
                        .with(user(principal(7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listRepos_authenticated_listsOnlyTheCallersRepos() throws Exception {
        when(gitRepositoryService.listReposForUser(7L)).thenReturn(List.of(repo(11L, "analytics")));

        mockMvc.perform(get("/api/git/local/repos").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("analytics"));

        verify(gitRepositoryService).listReposForUser(7L);
    }

    @Test
    void getRepo_authenticated_resolvesThroughTheOwnershipCheck() throws Exception {
        when(gitRepositoryService.getRepoForUser(7L, 11L)).thenReturn(repo(11L, "analytics"));

        mockMvc.perform(get("/api/git/local/repos/11").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11));

        verify(gitRepositoryService).getRepoForUser(7L, 11L);
    }

    @Test
    void listCommits_defaultPaging_requestsFirstPageOf50() throws Exception {
        when(gitRepositoryService.listCommitsForRepo(eq(7L), eq(11L), any()))
                .thenReturn(new PageImpl<>(List.of(commit(1L, "abc123"))));

        mockMvc.perform(get("/api/git/local/repos/11/commits").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].hash").value("abc123"));

        verify(gitRepositoryService).listCommitsForRepo(7L, 11L, PageRequest.of(0, 50));
    }

    @Test
    void listCommits_explicitPaging_passesPageAndSizeThrough() throws Exception {
        when(gitRepositoryService.listCommitsForRepo(eq(7L), eq(11L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/git/local/repos/11/commits")
                        .with(user(principal(7L)))
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        verify(gitRepositoryService).listCommitsForRepo(7L, 11L, PageRequest.of(1, 5));
    }

    @Test
    void collect_authenticated_reportsTheSavedCount() throws Exception {
        when(gitLocalCollector.collectForRepository(eq(11L), isNull())).thenReturn(12);

        mockMvc.perform(post("/api/git/local/repos/11/collect").with(user(principal(7L))))
                .andExpect(status().isOk())
                .andExpect(content().string("Collected 12 commits"));
    }

    @Test
    void listRepos_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/git/local/repos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void collect_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/git/local/repos/11/collect"))
                .andExpect(status().isUnauthorized());
    }
}
