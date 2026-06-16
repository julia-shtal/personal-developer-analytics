package com.juliashtal.devanalytics.auth.service;

import com.juliashtal.devanalytics.auth.model.RefreshToken;
import com.juliashtal.devanalytics.auth.repository.RefreshTokenRepository;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;

    RefreshTokenService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository);
        ReflectionTestUtils.setField(service, "refreshTokenExpirationMs", 3_600_000L);

        user = new User();
        user.setId(1L);
    }

    @Test
    void createRefreshToken_savesTokenWithExpiry() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        RefreshToken token = service.createRefreshToken(user);
        Instant after = Instant.now();

        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getExpiresAt()).isBetween(
                before.plusMillis(3_600_000L).minusSeconds(2),
                after.plusMillis(3_600_000L).plusSeconds(2));
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void rotateToken_revokesOldAndCreatesNew() {
        RefreshToken oldToken = new RefreshToken();
        oldToken.setUser(user);
        oldToken.setToken("old-token");
        oldToken.setExpiresAt(Instant.now().plusSeconds(60));

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken newToken = service.rotateToken(oldToken);

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(oldToken.getReplacedBy()).isEqualTo(newToken.getToken());
        assertThat(newToken.getUser()).isEqualTo(user);
        assertThat(newToken.getToken()).isNotEqualTo("old-token");
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void verifyToken_validToken_returnsToken() {
        RefreshToken token = new RefreshToken();
        token.setToken("valid-token");
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        assertThat(service.verifyToken("valid-token")).isSameAs(token);
    }

    @Test
    void verifyToken_unknownToken_throwsIllegalArgument() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyToken("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void verifyToken_expiredToken_throwsIllegalArgument() {
        RefreshToken token = new RefreshToken();
        token.setToken("expired-token");
        token.setUser(user);
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyToken("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyToken_revokedToken_revokesAllUserTokensAndThrows() {
        RefreshToken token = new RefreshToken();
        token.setToken("reused-token");
        token.setUser(user);
        token.setRevoked(true);
        token.setExpiresAt(Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findByToken("reused-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyToken("reused-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revoked");

        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    @Test
    void revokeAllUserTokens_delegatesToRepository() {
        service.revokeAllUserTokens(1L);

        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }
}
