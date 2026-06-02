package com.juliashtal.devanalytics.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        send(toEmail, "Password Reset Request - Dev Analytics", buildResetEmailBody(resetLink));
    }

    public void sendAiBriefEmail(String toEmail, String headline, LocalDate from, LocalDate to) {
        String subject = "Your weekly Dev Analytics summary";
        String body = """
                Hi,

                Your weekly productivity summary for %s – %s is ready.

                Headline: %s

                Log in to Dev Analytics to view the full breakdown and AI insights.

                Best regards,
                Dev Analytics
                """.formatted(from, to, headline.isBlank() ? "See your dashboard" : headline);
        send(toEmail, subject, body);
    }

    public void sendSyncFailureEmail(String toEmail, String dataSourceLabel) {
        String subject = "Dev Analytics: data sync failed";
        String body = """
                Hi,

                A data collection job for %s failed and could not complete.

                Log in to Dev Analytics to review the sync status and retry if needed.

                Best regards,
                Dev Analytics
                """.formatted(dataSourceLabel);
        send(toEmail, subject, body);
    }

    public void sendNewTeamMemberEmail(String toEmail, String teamName) {
        String subject = "You were added to a team on Dev Analytics";
        String body = """
                Hi,

                You have been added to the team "%s" on Dev Analytics.

                Log in to view your team dashboard and metrics.

                Best regards,
                Dev Analytics
                """.formatted(teamName);
        send(toEmail, subject, body);
    }

    public void sendAnomalyAlertEmail(String toEmail, List<String> anomalousMetrics, LocalDate from, LocalDate to) {
        String subject = "Dev Analytics: metric anomalies detected";
        String body = """
                Hi,

                Unusual patterns were detected in your metrics for %s – %s:

                %s

                These metrics deviate significantly from their recent trend.
                Log in to Dev Analytics to review your dashboard.

                Best regards,
                Dev Analytics
                """.formatted(from, to, String.join("\n", anomalousMetrics));
        send(toEmail, subject, body);
    }

    private void send(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
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
