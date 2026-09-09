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
 * <p>Gap recovery is deliberately not here. This job used to detect gaps from
 * {@code MAX(metric_snapshots.date)} and cap the recovered window at 30 days — but moving the
 * window start forward left the excluded days behind a watermark that the same run then
 * advanced past, so they were never revisited by any later run. Recovery moved to
 * {@link MetricBackfillScheduler}, which subtracts the {@code metric_coverage} ledger from the
 * user's collected history and therefore resumes rather than truncates.
 *
 * <p>Coverage for the day computed here is recorded by
 * {@link MetricsService#calculateDailyMetrics}, so this class holds no ledger logic of its own.
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
