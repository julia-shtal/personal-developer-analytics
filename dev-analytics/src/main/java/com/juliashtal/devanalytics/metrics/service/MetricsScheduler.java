package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class MetricsScheduler {

    private final MetricsService metricsService;
    private final UserRepository userRepository;

    // every day at 01:00
    //@Scheduled(cron = "0 0 1 * * ?")
    public void calculateYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        userRepository.findAll().forEach(user ->
                metricsService.calculateDailyMetrics(user.getId(), yesterday, yesterday)
        );
    }
}
