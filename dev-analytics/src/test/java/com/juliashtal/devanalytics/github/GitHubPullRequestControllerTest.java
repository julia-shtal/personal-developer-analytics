package com.juliashtal.devanalytics.github;

import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.github.controller.GitHubPullRequestController;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.pullrequest.GitHubPullRequestCollector;
import com.juliashtal.devanalytics.github.pullrequest.GitHubPullRequestQueryService;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link GitHubPullRequestController}.
 *
 * <p>Collection and listing are separate collaborators here — the orchestrator and the read
 * side — so each route is pinned to the one it is meant to call.</p>
 */
@WebMvcTest(GitHubPullRequestController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class GitHubPullRequestControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean GitHubPullRequestCollector prCollector;
    @MockBean GitHubPullRequestQueryService prQueryService;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor (HandlerInterceptor picked up by @WebMvcTest)
    @MockBean UserService userService;

    private static GitHubPullRequestEntity pr(long id, int number, String title) {
        GitHubPullRequestEntity e = new GitHubPullRequestEntity();
        e.setId(id);
        e.setNumber(number);
        e.setTitle(title);
        e.setState("open");
        e.setCreatedAt(Instant.parse("2026-09-01T09:00:00Z"));
        return e;
    }

    @Test
    @WithMockUser
    void collectPrs_authenticated_reportsTheProcessedCount() throws Exception {
        when(prCollector.collectForRepository(eq(11L), isNull())).thenReturn(8);

        mockMvc.perform(post("/api/github/repos/11/pull-requests/collect"))
                .andExpect(status().isOk())
                .andExpect(content().string("Processed 8 pull requests"));
    }

    @Test
    @WithMockUser
    void listPrs_defaultPaging_requestsFirstPageOf50() throws Exception {
        when(prQueryService.listPullRequests(eq(11L), any()))
                .thenReturn(new PageImpl<>(List.of(pr(1L, 42, "Add metric"))));

        mockMvc.perform(get("/api/github/repos/11/pull-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(42))
                .andExpect(jsonPath("$.content[0].title").value("Add metric"));

        verify(prQueryService).listPullRequests(11L, PageRequest.of(0, 50));
    }

    @Test
    @WithMockUser
    void listPrs_explicitPaging_passesPageAndSizeThrough() throws Exception {
        when(prQueryService.listPullRequests(eq(11L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/github/repos/11/pull-requests")
                        .param("page", "2").param("size", "10"))
                .andExpect(status().isOk());

        verify(prQueryService).listPullRequests(11L, PageRequest.of(2, 10));
    }

    @Test
    @WithMockUser
    void listPrs_unknownRepo_returns404() throws Exception {
        when(prQueryService.listPullRequests(eq(99L), any()))
                .thenThrow(new NoSuchElementException("Git repo not found: 99"));

        mockMvc.perform(get("/api/github/repos/99/pull-requests"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listPrs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/github/repos/11/pull-requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void collectPrs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/github/repos/11/pull-requests/collect"))
                .andExpect(status().isUnauthorized());
    }
}
