package com.juliashtal.devanalytics.notification;

import com.juliashtal.devanalytics.email.EmailService;
import com.juliashtal.devanalytics.notification.NotificationDispatchService;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserNotificationPrefsEntity;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.UserNotificationPrefsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock UserNotificationPrefsService prefsService;
    @Mock EmailService emailService;
    @Mock UserRepository userRepository;
    @Mock MetricsAnomalyService anomalyService;
    @InjectMocks
    NotificationDispatchService dispatcher;

    private User user;
    private UserNotificationPrefsEntity prefs;
    private final LocalDate from = LocalDate.of(2026, 1, 1);
    private final LocalDate to   = LocalDate.of(2026, 1, 7);

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        prefs = new UserNotificationPrefsEntity(user);
    }

    // ── AI brief ─────────────────────────────────────────────────────────────

    @Test
    void sendAiBriefIfEnabled_prefTrue_sendsEmail() {
        prefs.setAiBrief(true);
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);

        dispatcher.sendAiBriefIfEnabled(user, "Solid week", from, to);

        verify(emailService).sendAiBriefEmail("user@example.com", "Solid week", from, to);
    }

    @Test
    void sendAiBriefIfEnabled_prefFalse_doesNotSend() {
        prefs.setAiBrief(false);
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);

        dispatcher.sendAiBriefIfEnabled(user, "Solid week", from, to);

        verify(emailService, never()).sendAiBriefEmail(any(), any(), any(), any());
    }

    @Test
    void sendAiBriefIfEnabled_emailThrows_exceptionCaught() {
        prefs.setAiBrief(true);
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);
        doThrow(new RuntimeException("SMTP down")).when(emailService).sendAiBriefEmail(any(), any(), any(), any());

        assertDoesNotThrow(() -> dispatcher.sendAiBriefIfEnabled(user, "Headline", from, to));
    }

    // ── Sync failure ─────────────────────────────────────────────────────────

    @Test
    void sendSyncFailureIfEnabled_prefTrue_sendsEmail() {
        prefs.setSyncFailures(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);

        dispatcher.sendSyncFailureIfEnabled(1L, 42L);

        verify(emailService).sendSyncFailureEmail("user@example.com", "data source #42");
    }

    @Test
    void sendSyncFailureIfEnabled_prefFalse_doesNotSend() {
        prefs.setSyncFailures(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);

        dispatcher.sendSyncFailureIfEnabled(1L, 42L);

        verify(emailService, never()).sendSyncFailureEmail(any(), any());
    }

    @Test
    void sendSyncFailureIfEnabled_userNotFound_doesNotSend() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        dispatcher.sendSyncFailureIfEnabled(99L, 1L);

        verify(emailService, never()).sendSyncFailureEmail(any(), any());
    }

    // ── New team member ───────────────────────────────────────────────────────

    @Test
    void sendNewTeamMemberIfEnabled_prefTrue_sendsEmail() {
        prefs.setNewTeamMember(true);
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);

        dispatcher.sendNewTeamMemberIfEnabled(user, "Dev Team");

        verify(emailService).sendNewTeamMemberEmail("user@example.com", "Dev Team");
    }

    @Test
    void sendNewTeamMemberIfEnabled_prefFalse_doesNotSend() {
        prefs.setNewTeamMember(false);
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);

        dispatcher.sendNewTeamMemberIfEnabled(user, "Dev Team");

        verify(emailService, never()).sendNewTeamMemberEmail(any(), any());
    }

    // ── Anomaly alert ─────────────────────────────────────────────────────────

    @Test
    void sendAnomalyAlertIfEnabled_prefTrueAndAnomalyDetected_sendsEmail() {
        prefs.setAfterHours(true);
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);
        when(anomalyService.computeAnomalies(user, from, to))
                .thenReturn(Map.of(MetricType.DAILY_COMMITS_COUNT, true, MetricType.DAILY_CHURN_RATIO, false));

        dispatcher.sendAnomalyAlertIfEnabled(user, from, to);

        verify(emailService).sendAnomalyAlertEmail(eq("user@example.com"), any(), eq(from), eq(to));
    }

    @Test
    void sendAnomalyAlertIfEnabled_noAnomalies_doesNotSend() {
        prefs.setAfterHours(true);
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);
        when(anomalyService.computeAnomalies(user, from, to))
                .thenReturn(Map.of(MetricType.DAILY_COMMITS_COUNT, false, MetricType.DAILY_CHURN_RATIO, false));

        dispatcher.sendAnomalyAlertIfEnabled(user, from, to);

        verify(emailService, never()).sendAnomalyAlertEmail(any(), any(), any(), any());
    }

    @Test
    void sendAnomalyAlertIfEnabled_prefFalse_doesNotComputeOrSend() {
        prefs.setAfterHours(false);
        when(prefsService.getOrCreate(1L)).thenReturn(prefs);

        dispatcher.sendAnomalyAlertIfEnabled(user, from, to);

        verify(anomalyService, never()).computeAnomalies(any(), any(), any());
        verify(emailService, never()).sendAnomalyAlertEmail(any(), any(), any(), any());
    }
}
