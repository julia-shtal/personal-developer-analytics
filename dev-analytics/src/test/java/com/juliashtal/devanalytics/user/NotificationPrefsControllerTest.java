package com.juliashtal.devanalytics.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.controller.UserProfileController;
import com.juliashtal.devanalytics.user.model.ContactMethod;
import com.juliashtal.devanalytics.user.model.NotificationPrefsDto;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserNotificationPrefsEntity;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationPrefsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean UserService userService;
    @MockBean UserNotificationPrefsService notificationPrefsService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private UserNotificationPrefsEntity defaultPrefs() {
        User user = new User();
        user.setId(1L);
        return new UserNotificationPrefsEntity(user);
    }

    @Test
    @WithMockUser
    void getNotifications_returns200WithDefaults() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(notificationPrefsService.getOrCreate(1L)).thenReturn(defaultPrefs());

            mvc.perform(get("/api/users/me/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aiBrief").value(false))
                    .andExpect(jsonPath("$.syncFailures").value(false))
                    .andExpect(jsonPath("$.afterHours").value(false))
                    .andExpect(jsonPath("$.newTeamMember").value(false))
                    .andExpect(jsonPath("$.defaultContactMethod").value("IN_APP"));
        }
    }

    @Test
    @WithMockUser
    void putNotifications_roundTrips() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            NotificationPrefsDto dto = new NotificationPrefsDto(false, false, true, true, ContactMethod.EMAIL);
            when(notificationPrefsService.update(eq(1L), eq(dto))).thenReturn(dto);

            mvc.perform(put("/api/users/me/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aiBrief").value(false))
                    .andExpect(jsonPath("$.syncFailures").value(false))
                    .andExpect(jsonPath("$.afterHours").value(true))
                    .andExpect(jsonPath("$.newTeamMember").value(true))
                    .andExpect(jsonPath("$.defaultContactMethod").value("EMAIL"));
        }
    }
}