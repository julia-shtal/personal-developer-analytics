package com.juliashtal.devanalytics.auth;

import com.juliashtal.devanalytics.auth.model.*;
import com.juliashtal.devanalytics.auth.model.request.*;
import com.juliashtal.devanalytics.auth.service.PasswordResetService;
import com.juliashtal.devanalytics.invite.InviteInfoDto;
import com.juliashtal.devanalytics.invite.InviteService;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * REST controller for authentication. Mounted at /api/auth — register, login, token refresh, logout, and password reset.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    // Cookie path scoped to the refresh endpoint so the browser only sends it there.
    private static final String REFRESH_COOKIE_PATH = "/api/auth/refresh";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final InviteService inviteService;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    @Operation(summary = "Register a new user account")
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Authenticate and issue an access token; refresh token is set as an HttpOnly cookie")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthResponse auth = authService.login(request);
        setRefreshCookie(response, auth.getRefreshToken());
        auth.setRefreshToken(null);
        return ResponseEntity.ok(auth);
    }

    @Operation(summary = "Rotate the refresh token cookie and issue a new access token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request,
                                                HttpServletResponse response) {
        String token = readRefreshCookie(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthResponse auth = authService.refreshToken(token);
        setRefreshCookie(response, auth.getRefreshToken());
        auth.setRefreshToken(null);
        return ResponseEntity.ok(auth);
    }

    @Operation(summary = "Log out: increment the user's token version to invalidate all outstanding access tokens")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        // Cookie path = /api/auth/refresh, so browser doesn't send it here.
        // Identify the user from the JWT instead and revoke all their tokens.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails principal) {
            authService.logout(principal.getId());
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Request a password reset email")
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody PasswordResetRequest request) {
        passwordResetService.initiatePasswordReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Reset the password using a valid reset token")
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Look up an invite token — returns email, role, and team name; 400 if expired or used")
    @GetMapping("/invite/{token}")
    public ResponseEntity<InviteInfoDto> getInviteInfo(@PathVariable String token) {
        return ResponseEntity.ok(inviteService.validateInvite(token));
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(refreshExpirationMs / 1000)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
