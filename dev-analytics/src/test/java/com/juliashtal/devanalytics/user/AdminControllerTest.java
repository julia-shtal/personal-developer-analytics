package com.juliashtal.devanalytics.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.invite.InviteService;
import com.juliashtal.devanalytics.invite.model.CreateInviteRequest;
import com.juliashtal.devanalytics.invite.model.InviteTokenDto;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.controller.AdminController;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.request.UpdateRoleRequest;
import com.juliashtal.devanalytics.user.service.AdminService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link AdminController}.
 *
 * <p>Every route here is gated twice — by the {@code /api/admin/**} path rule in
 * {@link SecurityConfig} and by the class-level {@code @PreAuthorize} — so the filter chain is
 * imported to evaluate the rule that actually runs first.</p>
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean AdminService adminService;
    @MockBean InviteService inviteService;
    @MockBean CheckHelper checkHelper;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = user(1L, "root", Role.ADMIN);
        when(checkHelper.currentUser()).thenReturn(admin);
    }

    private static User user(long id, String username, Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setRole(role);
        return u;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStats_asAdmin_returnsAllThreeCounters() throws Exception {
        when(adminService.activeUsersLast24h()).thenReturn(7L);
        when(adminService.databaseSizeBytes()).thenReturn(4096L);
        when(adminService.aiCallsToday()).thenReturn(3L);

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeUsers24h").value(7))
                .andExpect(jsonPath("$.databaseSizeBytes").value(4096))
                .andExpect(jsonPath("$.aiCallsToday").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsers_withQuery_passesTheFilterThrough() throws Exception {
        when(userService.search("ali")).thenReturn(List.of(user(2L, "alice", Role.DEVELOPER)));

        mockMvc.perform(get("/api/admin/users").param("q", "ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));

        verify(userService).search("ali");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsers_noQuery_searchesWithNull() throws Exception {
        when(userService.search(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());

        verify(userService).search(null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUserRole_asAdmin_returnsUpdatedSummary() throws Exception {
        UpdateRoleRequest req = new UpdateRoleRequest();
        req.setRole(Role.MANAGER);
        when(userService.updateRole(2L, Role.MANAGER)).thenReturn(user(2L, "alice", Role.MANAGER));

        mockMvc.perform(put("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUser_asAdmin_returns200() throws Exception {
        mockMvc.perform(delete("/api/admin/users/2"))
                .andExpect(status().isOk());

        verify(userService).delete(2L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInvite_noRoleGiven_defaultsToDeveloper() throws Exception {
        CreateInviteRequest req = new CreateInviteRequest();
        req.setEmail("new@example.com");
        when(inviteService.createInvite(any(), eq("new@example.com"), eq(Role.DEVELOPER), any()))
                .thenReturn(new InviteTokenDto("tok", "new@example.com", "DEVELOPER",
                        Instant.parse("2026-10-01T00:00:00Z"), "https://app/invite/tok"));

        mockMvc.perform(post("/api/admin/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));

        verify(inviteService).createInvite(admin, "new@example.com", Role.DEVELOPER, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createInvite_lowercaseRole_isUppercasedBeforeLookup() throws Exception {
        CreateInviteRequest req = new CreateInviteRequest();
        req.setEmail("boss@example.com");
        req.setRole("manager");
        req.setTeamId(9L);
        when(inviteService.createInvite(any(), any(), any(), any()))
                .thenReturn(new InviteTokenDto("tok2", "boss@example.com", "MANAGER",
                        Instant.parse("2026-10-01T00:00:00Z"), "https://app/invite/tok2"));

        mockMvc.perform(post("/api/admin/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(inviteService).createInvite(admin, "boss@example.com", Role.MANAGER, 9L);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getStats_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DEVELOPER")
    void getAllUsers_asDeveloper_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStats_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }
}
