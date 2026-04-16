package com.juliashtal.devanalytics.auth.service;

import com.juliashtal.devanalytics.auth.repository.PasswordResetTokenRepository;
import com.juliashtal.devanalytics.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        refreshTokenRepository.deleteExpiredTokens(now);
        passwordResetTokenRepository.deleteExpiredOrUsedTokens(now);
        log.debug("Expired and used tokens cleaned up");
    }
}
