package com.juliashtal.devanalytics.ai.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.ai.model.MetricSummaryEntity;
import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.repository.MetricSummaryRepository;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Generates and persists a personal weekly AI summary for every user every Monday at 08:00 UTC.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsSummaryScheduler {

    private final UserRepository userRepository;
    private final MetricsAiService metricsAiService;
    private final MetricSummaryRepository summaryRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 0 8 * * MON", zone = "UTC")
    public void generateWeeklySummaries() {
        LocalDate to = LocalDate.now().minusDays(1);      // yesterday (Sunday)
        LocalDate from = to.minusDays(6);                  // the previous Monday

        log.info("Weekly AI summary job started: period {} to {}", from, to);

        userRepository.findAll().forEach(user -> {
            try {
                MetricsSummaryDto dto = metricsAiService.generateSummary(user, from, to, null);
                MetricSummaryEntity entity = toEntity(user, dto);
                summaryRepository.save(entity);
                log.debug("Saved weekly summary for userId={}", user.getId());
            } catch (Exception e) {
                log.error("Failed to generate weekly summary for userId={}: {}", user.getId(), e.getMessage(), e);
            }
        });

        log.info("Weekly AI summary job finished");
    }

    private MetricSummaryEntity toEntity(User user, MetricsSummaryDto dto) {
        MetricSummaryEntity entity = new MetricSummaryEntity();
        entity.setUser(user);
        entity.setPeriodFrom(dto.getFrom());
        entity.setPeriodTo(dto.getTo());
        entity.setScope(dto.getScope());
        entity.setRepoName(dto.getRepoName());
        entity.setOverview(dto.getOverview());
        entity.setModelName(dto.getModelName());
        entity.setRawModelOutput(dto.getRawModelOutput());
        try {
            entity.setInsights(objectMapper.writeValueAsString(dto.getInsights()));
            entity.setRecommendations(objectMapper.writeValueAsString(dto.getRecommendations()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize insights/recommendations for userId={}: {}", user.getId(), e.getMessage());
        }
        return entity;
    }
}
