package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.controller.TeamController;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeamController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamControllerDeleteTest {

    @Autowired MockMvc mvc;
    @MockBean TeamService teamService;
    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(roles = "MANAGER")
    void deleteTeam_happyPath_returns204() throws Exception {
        doNothing().when(teamService).deleteTeam(10L);

        mvc.perform(delete("/api/teams/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void deleteTeam_nonManager_returns403() throws Exception {
        doThrow(new ForbiddenException("Not the team manager")).when(teamService).deleteTeam(10L);

        mvc.perform(delete("/api/teams/10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void deleteTeam_attachedDatasource_returns409() throws Exception {
        doThrow(new ConflictException("Team has attached data sources")).when(teamService).deleteTeam(10L);

        mvc.perform(delete("/api/teams/10"))
                .andExpect(status().isConflict());
    }
}