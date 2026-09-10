package com.juliashtal.devanalytics.auth;

import com.juliashtal.devanalytics.auth.model.AuthResponse;
import com.juliashtal.devanalytics.auth.model.request.LoginRequest;
import com.juliashtal.devanalytics.auth.model.RefreshToken;
import com.juliashtal.devanalytics.auth.model.request.RegisterRequest;
import com.juliashtal.devanalytics.auth.service.RefreshTokenService;
import com.juliashtal.devanalytics.invite.InviteInfoDto;
import com.juliashtal.devanalytics.invite.InviteService;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserCommitEmail;
import com.juliashtal.devanalytics.user.repository.UserCommitEmailRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles registration, login, and logout; issues access tokens and rotates refresh tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserCommitEmailRepository commitEmailRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final InviteService inviteService;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already taken");
        }

        // Validate invite up front so we fail fast before creating the user
        InviteInfoDto inviteInfo = null;
        if (request.getInviteToken() != null && !request.getInviteToken().isBlank()) {
            inviteInfo = inviteService.validateInvite(request.getInviteToken());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(inviteInfo != null ? Role.valueOf(inviteInfo.role()) : Role.DEVELOPER);
        user.setGithubLogin(request.getGithubLogin());

        User saved = userRepository.save(user);
        seedCommitEmail(saved);

        if (inviteInfo != null) {
            inviteService.redeemInvite(request.getInviteToken(), saved);
        }

        log.info("New user registered: username={}, viaInvite={}", request.getUsername(), inviteInfo != null);
    }

    /**
     * Declares the registration address as the new user's first commit email, so their commits
     * are attributed from the very first collection without them configuring anything.
     *
     * <p>Only a seed: the resolver reads the table and never the account email, so changing the
     * account address later does not move attribution. Publishes no event — a user created one
     * statement ago has no metrics to invalidate — and skips silently when another account
     * already holds the address.</p>
     */
    private void seedCommitEmail(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) return;

        String email = user.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (commitEmailRepository.findByEmail(email).isPresent()) {
            log.info("Registration address already declared elsewhere; not seeding for userId={}", user.getId());
            return;
        }

        UserCommitEmail entity = new UserCommitEmail();
        entity.setUser(user);
        entity.setEmail(email);
        commitEmailRepository.save(entity);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsernameOrEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for identifier={}", request.getUsernameOrEmail());
            throw new BadCredentialsException("Invalid credentials");
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        log.info("User logged in: username={}", userDetails.getUsername());

        String accessToken = jwtService.generateAccessToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getUser());

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                jwtService.getAccessTokenExpirationMs() / 1000
        );
    }

    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken oldToken = refreshTokenService.verifyToken(refreshTokenValue);
        RefreshToken newToken = refreshTokenService.rotateToken(oldToken);

        CustomUserDetails userDetails = new CustomUserDetails(newToken.getUser());
        String accessToken = jwtService.generateAccessToken(userDetails);

        return new AuthResponse(
                accessToken,
                newToken.getToken(),
                jwtService.getAccessTokenExpirationMs() / 1000
        );
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAllUserTokens(userId);
        userRepository.incrementTokenVersion(userId);
        log.info("User logged out: userId={}", userId);
    }
}
