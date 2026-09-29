package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The token-version-logout invariant lives here: a JWT must carry the same {@code tokenVersion}
 * as the current {@link User} row, or logout (which bumps the version) would not actually
 * invalidate an outstanding access token.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthFilterTest {

    @Mock JwtService jwtService;
    @Mock CustomUserDetailsService userDetailsService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        // Built here, not as a field initializer: @Mock fields are injected by the extension
        // after instance construction, so building this eagerly would capture null mocks.
        filter = new JwtAuthFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static CustomUserDetails userDetails(long id, int tokenVersion) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        user.setRole(Role.DEVELOPER);
        user.setTokenVersion(tokenVersion);
        return new CustomUserDetails(user);
    }

    @Test
    void doFilterInternal_noAuthorizationHeader_proceedsUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_headerWithoutBearerPrefix_proceedsUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_malformedToken_extractUsernameThrows_proceedsUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer garbage");
        when(jwtService.extractUsername("garbage")).thenThrow(new RuntimeException("malformed JWT"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_alreadyAuthenticated_doesNotReauthenticate() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken("someone", null);
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.jwt");
        when(jwtService.extractUsername("valid.jwt")).thenReturn("alice");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void doFilterInternal_validTokenAndVersion_setsAuthenticationWithAuthorities() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.jwt");
        when(jwtService.extractUsername("valid.jwt")).thenReturn("alice");
        CustomUserDetails details = userDetails(1L, 0);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(details);
        when(jwtService.isTokenValid("valid.jwt", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("valid.jwt")).thenReturn(0);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isSameAs(details);
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_DEVELOPER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_tokenSignatureInvalid_doesNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad.jwt");
        when(jwtService.extractUsername("bad.jwt")).thenReturn("alice");
        CustomUserDetails details = userDetails(1L, 0);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(details);
        when(jwtService.isTokenValid("bad.jwt", details)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_tokenVersionStale_doesNotAuthenticate() throws Exception {
        // The token predates a logout (which bumps the user's tokenVersion) — it must not
        // still grant access.
        when(request.getHeader("Authorization")).thenReturn("Bearer stale.jwt");
        when(jwtService.extractUsername("stale.jwt")).thenReturn("alice");
        CustomUserDetails details = userDetails(1L, 2);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(details);
        when(jwtService.isTokenValid("stale.jwt", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("stale.jwt")).thenReturn(1);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_tokenVersionClaimMissing_doesNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer noversion.jwt");
        when(jwtService.extractUsername("noversion.jwt")).thenReturn("alice");
        CustomUserDetails details = userDetails(1L, 0);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(details);
        when(jwtService.isTokenValid("noversion.jwt", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("noversion.jwt")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_userDetailsNotCustomUserDetails_skipsVersionCheck() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.jwt");
        when(jwtService.extractUsername("valid.jwt")).thenReturn("bot-user");
        UserDetails generic = org.springframework.security.core.userdetails.User
                .withUsername("bot-user").password("x").authorities("ROLE_DEVELOPER").build();
        when(userDetailsService.loadUserByUsername("bot-user")).thenReturn(generic);
        when(jwtService.isTokenValid("valid.jwt", generic)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        // isTokenVersionValid short-circuits to true for a non-CustomUserDetails principal,
        // so extractTokenVersion is never consulted.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(jwtService, never()).extractTokenVersion(anyString());
    }

    @Test
    void shouldNotFilter_authPath_returnsTrue() {
        when(request.getServletPath()).thenReturn("/api/auth/login");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_nonAuthPath_returnsFalse() {
        when(request.getServletPath()).thenReturn("/api/datasources");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
