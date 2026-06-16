package com.juliashtal.devanalytics.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.controller.UserProfileController;
import com.juliashtal.devanalytics.user.model.ContactMethod;
import com.juliashtal.devanalytics.user.model.NotificationPrefsDto;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserNotificationPrefsEntity;
import com.juliashtal.devanalytics.user.model.request.ChangePasswordRequest;
import com.juliashtal.devanalytics.user.model.request.UpdateProfileRequest;
import com.juliashtal.devanalytics.user.service.UserNotificationPrefsService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserProfileControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService userService;
    @MockBean UserNotificationPrefsService notificationPrefsService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("julia");
        u.setEmail("julia@example.com");
        u.setRole(Role.DEVELOPER);
        u.setTimezone("Europe/Berlin");
        u.setGithubLogin("julia-shtal");
        return u;
    }

    @Test
    @WithMockUser
    void getProfile_returnsCurrentUserSummary() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(userService.getById(1L)).thenReturn(user(1L));

            mvc.perform(get("/api/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("julia"))
                    .andExpect(jsonPath("$.email").value("julia@example.com"))
                    .andExpect(jsonPath("$.role").value("DEVELOPER"))
                    .andExpect(jsonPath("$.githubLogin").value("julia-shtal"))
                    .andExpect(jsonPath("$.hasCustomAvatar").value(false));
        }
    }

    @Test
    @WithMockUser
    void updateProfile_validRequest_returnsUpdatedSummary() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("julia-updated");
        request.setTimezone("America/New_York");

        User updated = user(1L);
        updated.setUsername("julia-updated");
        updated.setTimezone("America/New_York");

        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(userService.updateProfile(eq(1L), any(UpdateProfileRequest.class))).thenReturn(updated);

            mvc.perform(put("/api/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("julia-updated"))
                    .andExpect(jsonPath("$.timezone").value("America/New_York"));
        }
    }

    @Test
    @WithMockUser
    void changePassword_validRequest_returns204() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldPassword123");
        request.setNewPassword("newPassword123");

        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            mvc.perform(put("/api/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @WithMockUser
    void changePassword_incorrectOldPassword_returns400() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("wrongPassword");
        request.setNewPassword("newPassword123");

        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            doThrow(new BadRequestException("Current password is incorrect"))
                    .when(userService).changePassword(1L, "wrongPassword", "newPassword123");

            mvc.perform(put("/api/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser
    void getNotifications_returnsPreferences() throws Exception {
        UserNotificationPrefsEntity entity = new UserNotificationPrefsEntity(user(1L));
        entity.setAiBrief(true);
        entity.setSyncFailures(true);
        entity.setAfterHours(false);
        entity.setNewTeamMember(true);
        entity.setDefaultContactMethod(ContactMethod.EMAIL);

        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(notificationPrefsService.getOrCreate(1L)).thenReturn(entity);

            mvc.perform(get("/api/users/me/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aiBrief").value(true))
                    .andExpect(jsonPath("$.syncFailures").value(true))
                    .andExpect(jsonPath("$.afterHours").value(false))
                    .andExpect(jsonPath("$.newTeamMember").value(true))
                    .andExpect(jsonPath("$.defaultContactMethod").value("EMAIL"));
        }
    }

    @Test
    @WithMockUser
    void updateNotifications_validRequest_returnsUpdatedPreferences() throws Exception {
        NotificationPrefsDto request = new NotificationPrefsDto(false, false, true, false, ContactMethod.IN_APP);
        NotificationPrefsDto updated = new NotificationPrefsDto(false, false, true, false, ContactMethod.IN_APP);

        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(notificationPrefsService.update(eq(1L), any(NotificationPrefsDto.class))).thenReturn(updated);

            mvc.perform(put("/api/users/me/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.afterHours").value(true))
                    .andExpect(jsonPath("$.defaultContactMethod").value("IN_APP"));
        }
    }
}
