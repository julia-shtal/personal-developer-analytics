package com.juliashtal.devanalytics.git;

import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.git.controller.RepoController;
import com.juliashtal.devanalytics.git.model.dto.RepoDto;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.github.issue.AsyncIssuesCollector;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link RepoController}.
 *
 * <p>Enabling issue collection hands the backfill to {@link AsyncIssuesCollector} rather than
 * running it inline, so the request must return without the collector having been awaited.</p>
 */
@WebMvcTest(RepoController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class RepoControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean RepoService repoService;
    @MockBean AsyncIssuesCollector asyncIssuesCollector;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor (HandlerInterceptor picked up by @WebMvcTest)
    @MockBean UserService userService;

    private static RepoDto repo(long id, boolean collectIssues) {
        return new RepoDto(id, "repo", "owner/repo", null, 3L, true,
                "https://github.com/owner/repo", collectIssues, null, null);
    }

    @Test
    @WithMockUser
    void listAccessible_noFilters_returns200() throws Exception {
        when(repoService.listAccessible(null, null)).thenReturn(List.of(repo(1L, false)));

        mockMvc.perform(get("/api/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repoFullName").value("owner/repo"));
    }

    @Test
    @WithMockUser
    void listAccessible_withFilters_passesBothThrough() throws Exception {
        when(repoService.listAccessible(3L, 9L)).thenReturn(List.of());

        mockMvc.perform(get("/api/repos").param("dataSourceId", "3").param("teamId", "9"))
                .andExpect(status().isOk());

        verify(repoService).listAccessible(3L, 9L);
    }

    @Test
    @WithMockUser
    void subscribe_authenticated_returns200() throws Exception {
        mockMvc.perform(post("/api/repos/1/subscribe"))
                .andExpect(status().isOk());

        verify(repoService).subscribe(1L);
    }

    @Test
    @WithMockUser
    void unsubscribe_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/repos/1/subscribe"))
                .andExpect(status().isNoContent());

        verify(repoService).unsubscribe(1L);
    }

    @Test
    @WithMockUser
    void setCollectIssues_enabled_passesTrueAndReturnsUpdatedRepo() throws Exception {
        when(repoService.setCollectIssues(eq(1L), eq(true), any())).thenReturn(repo(1L, true));

        mockMvc.perform(patch("/api/repos/1/collect-issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectIssues").value(true));
    }

    @Test
    @WithMockUser
    void setCollectIssues_missingFlag_isTreatedAsDisabled() throws Exception {
        when(repoService.setCollectIssues(eq(1L), eq(false), any())).thenReturn(repo(1L, false));

        mockMvc.perform(patch("/api/repos/1/collect-issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectIssues").value(false));

        verify(repoService).setCollectIssues(eq(1L), eq(false), any());
    }

    @Test
    void listAccessible_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/repos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscribe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/repos/1/subscribe"))
                .andExpect(status().isUnauthorized());
    }
}
