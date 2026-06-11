package com.juliashtal.devanalytics.auth.service;

import com.juliashtal.devanalytics.auth.model.PasswordResetToken;
import com.juliashtal.devanalytics.auth.repository.PasswordResetTokenRepository;
import com.juliashtal.devanalytics.email.EmailService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.password-reset.expiration}")
    private long tokenExpirationMs;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        // Silent fail — do not reveal whether the email is registered
        if (user == null) {
            log.debug("Password reset requested for unknown email — silently ignored");
            return;
        }

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setExpiresAt(Instant.now().plusMillis(tokenExpirationMs));
        tokenRepository.save(resetToken);

        String resetLink = frontendUrl + "/reset-password?token=" + resetToken.getToken();
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            log.info("Password reset email sent for userId={}", user.getId());
        } catch (Exception e) {
            log.error("Password reset email failed for userId={}: {}", user.getId(), e.getMessage());
        }
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> {
                    log.warn("Password reset attempt with invalid token");
                    return new IllegalArgumentException("Invalid reset token");
                });

        if (resetToken.isUsed()) {
            log.warn("Password reset attempt with already-used token for userId={}", resetToken.getUser().getId());
            throw new IllegalArgumentException("Reset token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Password reset attempt with expired token for userId={}", resetToken.getUser().getId());
            throw new IllegalArgumentException("Reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
        log.info("Password successfully reset for userId={}", user.getId());
    }
}
