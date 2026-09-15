package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.controller.TeamController;
import com.juliashtal.devanalytics.user.model.TeamMembershipDto;
import com.juliashtal.devanalytics.user.service.TeamService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins that a member with no managing role can read their own team memberships while the rest of
 * {@code /api/teams} stays manager-only.
 *
 * <p>{@link SecurityConfig} is imported because the {@code authorizeHttpRequests} path rules are
 * evaluated before any controller, and a slice without them cannot observe a rejection.</p>
 */
@WebMvcTest(TeamController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class TeamMembershipsControllerTest {

    @Autowired MockMvc mvc;

    @MockBean TeamService teamService;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor (HandlerInterceptor picked up by @WebMvcTest)
    @MockBean UserService userService;

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void getMyMemberships_asDeveloper_returns200() throws Exception {
        TeamMembershipDto dto = new TeamMembershipDto(5L, "Backend Squad", 0);
        when(teamService.getMyMemberships()).thenReturn(List.of(dto));

        mvc.perform(get("/api/teams/me/memberships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].name").value("Backend Squad"));
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void getMyMemberships_noTeams_returnsEmptyList() throws Exception {
        when(teamService.getMyMemberships()).thenReturn(List.of());

        mvc.perform(get("/api/teams/me/memberships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getMyMemberships_asManager_returns200() throws Exception {
        when(teamService.getMyMemberships()).thenReturn(List.of());

        mvc.perform(get("/api/teams/me/memberships"))
                .andExpect(status().isOk());
    }

    @Test
    void getMyMemberships_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/teams/me/memberships"))
                .andExpect(status().isUnauthorized());
    }

    /** The self-scoped exemption must not widen to the manager-only endpoints beside it. */
    @Test
    @WithMockUser(roles = "DEVELOPER")
    void getMyTeams_asDeveloper_returns403() throws Exception {
        mvc.perform(get("/api/teams"))
                .andExpect(status().isForbidden());
    }

    /**
     * Every sibling route binds {@code me} to a {@code Long} {@code teamId}, so a subtree
     * exemption would hand these to the controller and let type conversion decide the outcome.
     */
    @Test
    @WithMockUser(roles = "DEVELOPER")
    void renameTeamNamedMe_asDeveloper_returns403NotAConversionError() throws Exception {
        mvc.perform(put("/api/teams/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void exportTeamNamedMe_asDeveloper_returns403() throws Exception {
        mvc.perform(get("/api/teams/me/export").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isForbidden());
    }
}
