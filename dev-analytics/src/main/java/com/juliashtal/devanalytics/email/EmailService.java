package com.juliashtal.devanalytics.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from-address}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

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
        send(toEmail, subject, asHtml(body));
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
        send(toEmail, subject, asHtml(body));
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
        send(toEmail, subject, asHtml(body));
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
        send(toEmail, subject, asHtml(body));
    }

    private void send(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (jakarta.mail.MessagingException | java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("Failed to build email message", e);
        }
    }

    private String buildResetEmailBody(String resetLink) {
        return """
                <html>
                <body style="font-family: sans-serif; color: #1a1a1a; line-height: 1.5;">
                  <p>Hello,</p>
                  <p>You have requested to reset your password for Dev Analytics.</p>
                  <p>
                    <a href="%s" style="color: #6d28d9;">Click here to reset your password</a>
                    (valid for 1 hour).
                  </p>
                  <p>If you did not request this, please ignore this email.</p>
                  <p>Best regards,<br>Dev Analytics Team</p>
                </body>
                </html>
                """.formatted(resetLink);
    }

    private String asHtml(String plainTextBody) {
        return "<html><body style=\"font-family: sans-serif; white-space: pre-wrap;\">" + plainTextBody + "</body></html>";
    }
}
