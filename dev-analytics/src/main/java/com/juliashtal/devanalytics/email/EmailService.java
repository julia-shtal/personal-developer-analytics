package com.juliashtal.devanalytics.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Password Reset Request - Dev Analytics");
        message.setText(buildResetEmailBody(resetLink));
        mailSender.send(message);
    }

    private String buildResetEmailBody(String resetLink) {
        return """
                Hello,

                You have requested to reset your password for Dev Analytics.

                Click the link below to reset your password (valid for 1 hour):
                %s

                If you did not request this, please ignore this email.

                Best regards,
                Dev Analytics Team
                """.formatted(resetLink);
    }
}
