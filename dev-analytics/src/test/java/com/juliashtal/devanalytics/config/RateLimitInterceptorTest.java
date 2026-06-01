package com.juliashtal.devanalytics.config;

import com.juliashtal.devanalytics.exception.RateLimitExceededException;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock CustomUserDetails principal;

    RateLimitInterceptor interceptor;
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor();
        // Allow 1 req/min on AI tier and 5 req/min default for testing
        ReflectionTestUtils.setField(interceptor, "aiRpm", 1);
        ReflectionTestUtils.setField(interceptor, "defaultRpm", 5);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private void authenticateAs(long userId) {
        when(principal.getId()).thenReturn(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void firstRequest_withinLimit_passes() throws Exception {
        authenticateAs(42L);
        request.setRequestURI("/api/ai/summary");
        boolean result = interceptor.preHandle(request, response, null);
        assertThat(result).isTrue();
    }

    @Test
    void secondAiRequest_exceedsLimit_throws() throws Exception {
        authenticateAs(42L);
        request.setRequestURI("/api/ai/summary");
        interceptor.preHandle(request, response, null); // consumes the 1 allowed token

        assertThatThrownBy(() -> interceptor.preHandle(request, response, null))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Retry after");
    }

    @Test
    void defaultEndpoint_doesNotShareAiBucket() throws Exception {
        authenticateAs(42L);
        request.setRequestURI("/api/ai/summary");
        interceptor.preHandle(request, response, null); // exhaust AI bucket

        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setRequestURI("/api/metrics");
        assertThat(interceptor.preHandle(other, response, null)).isTrue();
    }

    @Test
    void unauthenticatedRequest_passesThrough() throws Exception {
        // No SecurityContext set — interceptor should pass through
        request.setRequestURI("/api/auth/register");
        assertThat(interceptor.preHandle(request, response, null)).isTrue();
    }
}
