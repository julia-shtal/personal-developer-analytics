package com.juliashtal.devanalytics.config;

import com.juliashtal.devanalytics.config.controller.SpaFallbackController;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link SpaFallbackController}.
 *
 * <p>Client-side routes must reach index.html without a login redirect, or a deep link into the
 * SPA 404s before React Router ever sees it. Every declared route is checked, unauthenticated.</p>
 */
@WebMvcTest(SpaFallbackController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class SpaFallbackControllerTest {

    @Autowired MockMvc mockMvc;

    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor (HandlerInterceptor picked up by @WebMvcTest)
    @MockBean UserService userService;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/login", "/register", "/forgot-password", "/reset-password",
            "/welcome", "/dashboard", "/team", "/team-manage", "/datasources",
            "/settings", "/messages", "/goals", "/admin"})
    void spa_declaredRoute_forwardsToIndexHtmlWithoutAuth(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void spa_undeclaredRoute_isNotForwarded() throws Exception {
        mockMvc.perform(get("/not-a-spa-route"))
                .andExpect(forwardedUrl(null));
    }
}
