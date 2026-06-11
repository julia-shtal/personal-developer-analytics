package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.controller.TeamController;
import com.juliashtal.devanalytics.user.model.TeamMembershipDto;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeamController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamMembershipsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean TeamService teamService;
    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

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
}
