package com.juliashtal.devanalytics.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.auth.model.AuthResponse;
import com.juliashtal.devanalytics.auth.model.request.LoginRequest;
import com.juliashtal.devanalytics.auth.service.PasswordResetService;
import com.juliashtal.devanalytics.invite.InviteService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerCookieTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean AuthService authService;
    @MockBean PasswordResetService passwordResetService;
    @MockBean InviteService inviteService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    @MockBean UserService userService;
    @MockBean CheckHelper checkHelper;

    @Test
    void login_setsHttpOnlyCookie_andOmitsRefreshTokenFromBody() throws Exception {
        AuthResponse stub = new AuthResponse("access-123", "refresh-abc", 900);
        when(authService.login(any())).thenReturn(stub);

        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("alice");
        req.setPassword("pass");

        var result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-123"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("refresh_token=refresh-abc");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/api/auth/refresh");
        assertThat(setCookie).contains("SameSite=Strict");
    }

    @Test
    void refresh_withValidCookie_returnsNewAccessToken_andRotatesCookie() throws Exception {
        AuthResponse stub = new AuthResponse("access-new", "refresh-new", 900);
        when(authService.refreshToken("refresh-old")).thenReturn(stub);

        var result = mvc.perform(post("/api/auth/refresh")
                        .cookie(new MockCookie("refresh_token", "refresh-old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-new"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refresh_token=refresh-new");
        assertThat(setCookie).contains("HttpOnly");
    }

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void logout_clearsCookie() throws Exception {
        var result = mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refresh_token=");
        assertThat(setCookie).contains("Max-Age=0");
    }
}
