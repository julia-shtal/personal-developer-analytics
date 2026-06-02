package com.juliashtal.devanalytics.notification;

import com.juliashtal.devanalytics.email.EmailService;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.UserNotificationPrefsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Routes notification events to EmailService, gated by each user's notification preferences.
 * All sends are best-effort: exceptions are caught and logged so a mail failure never breaks
 * the triggering operation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final UserNotificationPrefsService prefsService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final MetricsAnomalyService anomalyService;

    /** Called after a weekly AI summary is generated. Gates on aiBrief pref. */
    @Async("notificationTaskExecutor")
    public void sendAiBriefIfEnabled(User user, String headline, LocalDate from, LocalDate to) {
        try {
            if (!prefsService.getOrCreate(user.getId()).isAiBrief()) return;
            emailService.sendAiBriefEmail(user.getEmail(), headline, from, to);
            log.debug("AI brief email sent to userId={}", user.getId());
        } catch (Exception e) {
            log.error("AI brief email failed for userId={}: {}", user.getId(), e.getMessage());
        }
    }

    /** Called after a sync job fails. Gates on syncFailures pref. */
    @Async("notificationTaskExecutor")
    public void sendSyncFailureIfEnabled(Long userId, Long dataSourceId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;
            if (!prefsService.getOrCreate(userId).isSyncFailures()) return;
            emailService.sendSyncFailureEmail(user.getEmail(), "data source #" + dataSourceId);
            log.debug("Sync failure email sent to userId={}", userId);
        } catch (Exception e) {
            log.error("Sync failure email failed for userId={}: {}", userId, e.getMessage());
        }
    }

    /** Called after a user is added to a team. Gates on newTeamMember pref. */
    @Async("notificationTaskExecutor")
    public void sendNewTeamMemberIfEnabled(User newMember, String teamName) {
        try {
            if (!prefsService.getOrCreate(newMember.getId()).isNewTeamMember()) return;
            emailService.sendNewTeamMemberEmail(newMember.getEmail(), teamName);
            log.debug("New team member email sent to userId={}", newMember.getId());
        } catch (Exception e) {
            log.error("New team member email failed for userId={}: {}", newMember.getId(), e.getMessage());
        }
    }

    /**
     * Called after a weekly AI summary is generated. Computes anomalies for the same period
     * and emails the user if any are found. Gates on afterHours pref.
     */
    @Async("notificationTaskExecutor")
    public void sendAnomalyAlertIfEnabled(User user, LocalDate from, LocalDate to) {
        try {
            if (!prefsService.getOrCreate(user.getId()).isAfterHours()) return;
            List<String> anomalousMetrics = anomalyService.computeAnomalies(user, from, to)
                    .entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(e -> e.getKey().name())
                    .toList();
            if (anomalousMetrics.isEmpty()) return;
            emailService.sendAnomalyAlertEmail(user.getEmail(), anomalousMetrics, from, to);
            log.debug("Anomaly alert email sent to userId={}, metrics={}", user.getId(), anomalousMetrics);
        } catch (Exception e) {
            log.error("Anomaly alert email failed for userId={}: {}", user.getId(), e.getMessage());
        }
    }
}
