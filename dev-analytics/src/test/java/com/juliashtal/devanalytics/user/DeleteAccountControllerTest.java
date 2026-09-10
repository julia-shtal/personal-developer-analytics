package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.controller.UserProfileController;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
import com.juliashtal.devanalytics.user.service.UserNotificationPrefsService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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

@WebMvcTest(UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeleteAccountControllerTest {

    @Autowired MockMvc mvc;
    @MockBean UserService userService;
    @MockBean UserNotificationPrefsService notificationPrefsService;
    @MockBean AuthorIdentityService authorIdentityService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser
    void deleteAccount_regularUser_returns204() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            doNothing().when(userService).deleteSelf(1L);

            mvc.perform(delete("/api/users/me"))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @WithMockUser
    void deleteAccount_lastAdmin_returns409() throws Exception {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            doThrow(new ConflictException("Cannot delete the last admin account — promote another user first."))
                    .when(userService).deleteSelf(1L);

            mvc.perform(delete("/api/users/me"))
                    .andExpect(status().isConflict());
        }
    }
}
