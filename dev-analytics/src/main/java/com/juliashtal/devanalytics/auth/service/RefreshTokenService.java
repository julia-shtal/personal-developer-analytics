package com.juliashtal.devanalytics.auth.service;

import com.juliashtal.devanalytics.auth.model.RefreshToken;
import com.juliashtal.devanalytics.auth.repository.RefreshTokenRepository;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshTokenExpirationMs;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(generateSecureToken());
        token.setExpiresAt(Instant.now().plusMillis(refreshTokenExpirationMs));
        return refreshTokenRepository.save(token);
    }

    @Transactional
    public RefreshToken rotateToken(RefreshToken oldToken) {
        RefreshToken newToken = createRefreshToken(oldToken.getUser());

        oldToken.setRevoked(true);
        oldToken.setReplacedBy(newToken.getToken());
        refreshTokenRepository.save(oldToken);

        return newToken;
    }

    public RefreshToken verifyToken(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (token.isRevoked()) {
            // Potential token reuse attack — revoke entire session
            revokeAllUserTokens(token.getUser().getId());
            throw new IllegalArgumentException("Refresh token was revoked");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token has expired");
        }

        return token;
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private String generateSecureToken() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }
}
