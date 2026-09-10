package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * The incremental daily job: computes yesterday's personal metrics for every user.
 *
 * <p>Gap recovery belongs to {@link MetricBackfillScheduler}, which resumes from the
 * {@code metric_coverage} ledger rather than a watermark. Coverage for the day computed here is
 * recorded by {@link MetricsService#calculateDailyMetrics}, so this class holds no ledger logic.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsScheduler {

    private final MetricsService metricsService;
    private final UserRepository userRepository;

    /** Runs every day at 01:00 server time. */
    @Scheduled(cron = "0 0 1 * * ?")
    public void calculateYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Nightly metrics scheduler started for {}", yesterday);

        userRepository.findAll().forEach(user -> {
            try {
                metricsService.calculateDailyMetrics(user.getId(), yesterday, yesterday);
                log.debug("Computed metrics for userId={} on {}", user.getId(), yesterday);
            } catch (Exception e) {
                log.warn("Nightly metrics failed for userId={}: {}", user.getId(), e.getMessage());
            }
        });

        log.info("Nightly metrics scheduler finished");
    }
}
