package com.juliashtal.devanalytics.ai.scheduler;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.config.SystemClock;
import com.juliashtal.devanalytics.metrics.service.MetricWriteGate;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.notification.NotificationDispatchService;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Generates and persists a personal weekly AI summary for every user every Monday at 08:00 UTC.
 * Persistence is handled inside MetricsAiService.generateSummary via MetricSummaryPersistenceService.
 * After each summary, fires notification emails (AI brief and anomaly alerts) per user preferences.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsSummaryScheduler {

    private final UserRepository userRepository;
    private final MetricsService metricsService;
    private final MetricsAiService metricsAiService;
    private final NotificationDispatchService notificationDispatch;
    private final SystemClock systemClock;
    private final MetricWriteGate writeGate;

    @Scheduled(cron = "0 0 8 * * MON", zone = "UTC")
    public void generateWeeklySummaries() {
        LocalDate to = systemClock.yesterday();
        LocalDate from = to.minusDays(6);

        log.info("Weekly AI summary job started: period {} to {}", from, to);

        userRepository.findAll().forEach(user -> {
            try {
                // Compute the week before summarising it: the nightly job may not have covered this ISO week.
                // Only the refresh is gated — a summary over stored snapshots still beats no summary
                // for a week, which is what skipping the whole run would cost at this cadence.
                boolean refreshed = writeGate.runExclusively(
                        () -> metricsService.calculateDailyMetrics(user.getId(), from, to));
                if (!refreshed) {
                    log.info("Metric refresh skipped for userId={}; summarising stored snapshots", user.getId());
                }

                MetricsSummaryDto summary = metricsAiService.generateSummary(user, from, to, null);
                log.debug("Generated weekly summary for userId={}", user.getId());
                notificationDispatch.sendAiBriefIfEnabled(user, summary.getHeadline(), from, to);
                notificationDispatch.sendAnomalyAlertIfEnabled(user, from, to);
            } catch (Exception e) {
                log.error("Failed to generate weekly summary for userId={}: {}", user.getId(), e.getMessage(), e);
            }
        });

        log.info("Weekly AI summary job finished");
    }
}
