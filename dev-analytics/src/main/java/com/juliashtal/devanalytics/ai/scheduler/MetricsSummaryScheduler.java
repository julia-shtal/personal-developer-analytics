package com.juliashtal.devanalytics.ai.scheduler;

import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
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

    @Scheduled(cron = "0 0 8 * * MON", zone = "UTC")
    public void generateWeeklySummaries() {
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(6);

        log.info("Weekly AI summary job started: period {} to {}", from, to);

        userRepository.findAll().forEach(user -> {
            try {
                // Compute the week before summarising it. The nightly job only guarantees rows
                // up to yesterday at its own grain; without this pass the aggregate calculators
                // may never have been run over the ISO week this summary is about, and the
                // context would be built from a partial catalogue.
                metricsService.calculateDailyMetrics(user.getId(), from, to);

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
