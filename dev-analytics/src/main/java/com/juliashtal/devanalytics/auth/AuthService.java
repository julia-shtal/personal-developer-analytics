package com.juliashtal.devanalytics.auth;

import com.juliashtal.devanalytics.auth.model.AuthResponse;
import com.juliashtal.devanalytics.auth.model.request.LoginRequest;
import com.juliashtal.devanalytics.auth.model.RefreshToken;
import com.juliashtal.devanalytics.auth.model.request.RegisterRequest;
import com.juliashtal.devanalytics.auth.service.RefreshTokenService;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public void register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already taken");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.DEVELOPER);
        user.setGithubLogin(request.getGithubLogin());

        userRepository.save(user);
        log.info("New user registered: username={}", request.getUsername());
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
    public void logout(String refreshTokenValue) {
        RefreshToken token = refreshTokenService.verifyToken(refreshTokenValue);
        Long userId = token.getUser().getId();
        refreshTokenService.revokeAllUserTokens(userId);
        userRepository.incrementTokenVersion(userId);
        log.info("User logged out: userId={}", userId);
    }
}
