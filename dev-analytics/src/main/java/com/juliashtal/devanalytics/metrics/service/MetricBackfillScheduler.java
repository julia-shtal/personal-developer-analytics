package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.metrics.model.BackfillResult;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link MetricBackfillService} over every user once a night, filling days inside each
 * user's collected history that have never been calculated.
 *
 * <p>Separate from {@link MetricsScheduler}, which only computes yesterday. Runs at 03:00 UTC;
 * ordering against that job is not a correctness requirement, because both write through the
 * same {@link MetricsService#calculateDailyMetrics} upsert guard.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricBackfillScheduler {

    private final UserRepository userRepository;
    private final MetricBackfillService backfillService;

    @Scheduled(cron = "0 0 3 * * ?", zone = "UTC")
    public void backfillAll() {
        log.info("History backfill job started");

        userRepository.findAll().forEach(user -> {
            try {
                BackfillResult result = backfillService.backfillUser(user.getId());
                if (result.daysComputed() > 0) {
                    log.info("Backfill for userId={}: {} day(s) computed, {} remaining",
                            user.getId(), result.daysComputed(), result.daysRemaining());
                }
            } catch (Exception e) {
                // One user's failure must not abort the run. The exception is passed whole, not as
                // getMessage(): this runs unattended, so the failure site must survive in the log.
                log.warn("Backfill failed for userId={}", user.getId(), e);
            }
        });

        log.info("History backfill job finished");
    }
}
