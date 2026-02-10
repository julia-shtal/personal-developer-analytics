package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
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
