package com.juliashtal.devanalytics.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(inv ->
                new MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));

        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@devanalytics.local");
        ReflectionTestUtils.setField(emailService, "fromName", "Dev Analytics");
    }

    @Test
    void sendPasswordResetEmail_setsConfiguredFromAddressAndName() throws Exception {
        emailService.sendPasswordResetEmail("user@example.com", "https://app.example.com/reset?token=abc");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getFrom()).hasSize(1);
        assertThat(sent.getFrom()[0].toString()).isEqualTo("Dev Analytics <no-reply@devanalytics.local>");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("Password Reset Request - Dev Analytics");
    }

    @Test
    void sendAiBriefEmail_setsConfiguredFromAddressAndName() throws Exception {
        emailService.sendAiBriefEmail("user@example.com", "Great week!", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 8));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getFrom()[0].toString()).isEqualTo("Dev Analytics <no-reply@devanalytics.local>");
    }
}