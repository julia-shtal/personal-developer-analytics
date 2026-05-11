package com.juliashtal.devanalytics.metrics.service;

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

    private final MetricsService metricsService;
    private final UserRepository userRepository;

    /** Runs every day at 01:00 server time — calculates yesterday's metrics for all users. */
    @Scheduled(cron = "0 0 1 * * ?")
    public void calculateYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Nightly metrics scheduler: calculating for {}", yesterday);
        userRepository.findAll().forEach(user -> {
            try {
                metricsService.calculateDailyMetrics(user.getId(), yesterday, yesterday);
            } catch (Exception e) {
                log.warn("Nightly metrics failed for user {}: {}", user.getId(), e.getMessage());
            }
        });
        log.info("Nightly metrics scheduler: finished for {}", yesterday);
    }
}
