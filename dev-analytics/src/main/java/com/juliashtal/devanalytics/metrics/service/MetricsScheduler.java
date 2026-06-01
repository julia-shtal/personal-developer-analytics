package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsScheduler {

    public static final int MAX_BACKFILL_DAYS = 30;

    private final MetricsService metricsService;
    private final UserRepository userRepository;
    private final MetricSnapshotRepository snapshotRepository;

    /**
     * Runs every day at 01:00 server time.
     * Computes every missing day since the user's last snapshot, not just yesterday,
     * so a multi-day outage is fully recovered. Window is capped at 30 days to
     * prevent runaway backfills.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void calculateYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate cap = yesterday.minusDays(MAX_BACKFILL_DAYS - 1);
        log.info("Nightly metrics scheduler started, target range up to {}", yesterday);

        userRepository.findAll().forEach(user -> {
            try {
                LocalDate lastComputed = snapshotRepository.findMaxPersonalDate(user.getId())
                        .orElse(null);

                LocalDate from;
                if (lastComputed == null) {
                    from = yesterday;
                } else {
                    from = lastComputed.plusDays(1);
                }

                if (!from.isAfter(yesterday)) {
                    if (from.isBefore(cap)) {
                        log.warn("Backfill window capped for userId={}: requested from={}, capped to={}",
                                user.getId(), from, cap);
                        from = cap;
                    }
                    metricsService.calculateDailyMetrics(user.getId(), from, yesterday);
                    log.debug("Computed metrics for userId={}: {} → {}", user.getId(), from, yesterday);
                }
            } catch (Exception e) {
                log.warn("Nightly metrics failed for userId={}: {}", user.getId(), e.getMessage());
            }
        });

        log.info("Nightly metrics scheduler finished");
    }
}
