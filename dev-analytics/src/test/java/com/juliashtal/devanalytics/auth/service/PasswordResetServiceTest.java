package com.juliashtal.devanalytics.auth.service;

import com.juliashtal.devanalytics.auth.model.PasswordResetToken;
import com.juliashtal.devanalytics.auth.repository.PasswordResetTokenRepository;
import com.juliashtal.devanalytics.email.EmailService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;

    PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokenRepository, userRepository, passwordEncoder, emailService);
        ReflectionTestUtils.setField(service, "tokenExpirationMs", 3_600_000L);
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173");
    }

    @Test
    void initiatePasswordReset_emailSendThrows_doesNotPropagateException() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        doThrow(new MailSendException("smtp failure"))
                .when(emailService).sendPasswordResetEmail(anyString(), anyString());

        assertThatCode(() -> service.initiatePasswordReset("alice@example.com"))
                .doesNotThrowAnyException();

        verify(tokenRepository).save(any());
    }

    @Test
    void initiatePasswordReset_unknownEmail_doesNotSaveTokenOrSendEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.initiatePasswordReset("ghost@example.com");

        verifyNoInteractions(tokenRepository, emailService);
    }

    @Test
    void initiatePasswordReset_validEmail_savesTokenAndSendsResetEmail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        Instant before = Instant.now();
        service.initiatePasswordReset("alice@example.com");
        Instant after = Instant.now();

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getToken()).isNotBlank();
        assertThat(savedToken.getExpiresAt()).isAfter(before.plusMillis(3_600_000L - 1000));
        assertThat(savedToken.getExpiresAt()).isBefore(after.plusMillis(3_600_000L + 1000));

        verify(emailService).sendPasswordResetEmail(
                eq("alice@example.com"),
                contains("/reset-password?token=" + savedToken.getToken()));
    }
}
