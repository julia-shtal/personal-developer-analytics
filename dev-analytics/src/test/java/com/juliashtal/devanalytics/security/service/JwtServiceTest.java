package com.juliashtal.devanalytics.security.service;

import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-256-bits-long-for-hs256!!";

    private JwtService jwtService(long expirationMs) {
        return new JwtService(SECRET, expirationMs);
    }

    private CustomUserDetails userDetails() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setRole(Role.MANAGER);
        user.setTokenVersion(3);
        return new CustomUserDetails(user);
    }

    @Test
    void generateAccessToken_thenExtractUsername_roundTrips() {
        JwtService service = jwtService(900_000L);
        String token = service.generateAccessToken(userDetails());

        assertThat(service.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void generateAccessToken_includesTokenVersionClaim() {
        JwtService service = jwtService(900_000L);
        String token = service.generateAccessToken(userDetails());

        assertThat(service.extractTokenVersion(token)).isEqualTo(3);
    }

    @Test
    void isTokenValid_matchingUserAndUnexpired_returnsTrue() {
        JwtService service = jwtService(900_000L);
        CustomUserDetails details = userDetails();
        String token = service.generateAccessToken(details);

        assertThat(service.isTokenValid(token, details)).isTrue();
    }

    @Test
    void isTokenValid_expiredToken_throwsExpiredJwtException() {
        JwtService service = jwtService(-1_000L);
        CustomUserDetails details = userDetails();
        String token = service.generateAccessToken(details);

        org.junit.jupiter.api.Assertions.assertThrows(
                io.jsonwebtoken.ExpiredJwtException.class,
                () -> service.isTokenValid(token, details));
    }

    @Test
    void getAccessTokenExpirationMs_returnsConfiguredValue() {
        JwtService service = jwtService(900_000L);

        assertThat(service.getAccessTokenExpirationMs()).isEqualTo(900_000L);
    }
}