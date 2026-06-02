package com.juliashtal.devanalytics.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juliashtal.devanalytics.ai.model.MetricSummaryEntity;
import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.repository.MetricSummaryRepository;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricSummaryPersistenceService {

    private final MetricSummaryRepository summaryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void savePersonal(User user, MetricsSummaryDto dto) {
        if (dto.getHeadline() == null || (dto.getHeadline().isEmpty() && dto.getInsights().isEmpty())) {
            log.warn("Skipping persistence for userId={}: parse produced empty result", user.getId());
            return;
        }
        upsert(user, null, dto);
    }

    @Transactional
    public void saveTeam(Team team, MetricsSummaryDto dto) {
        if (dto.getHeadline() == null || (dto.getHeadline().isEmpty() && dto.getInsights().isEmpty())) {
            log.warn("Skipping persistence for teamId={}: parse produced empty result", team.getId());
            return;
        }
        upsert(null, team, dto);
    }

    public Optional<MetricsSummaryDto> findLatestPersonal(User user) {
        return summaryRepository.findTopByUser_IdOrderByGeneratedAtDesc(user.getId())
                .map(this::toDto);
    }

    public Optional<MetricsSummaryDto> findLatestTeam(Team team) {
        return summaryRepository.findTopByTeam_IdOrderByGeneratedAtDesc(team.getId())
                .map(this::toDto);
    }

    public List<MetricsSummaryDto> findHistoryPersonal(User user, int limit) {
        return summaryRepository
                .findByUser_IdOrderByGeneratedAtDesc(user.getId(), PageRequest.of(0, limit))
                .stream().map(this::toDto).toList();
    }

    public List<MetricsSummaryDto> findHistoryTeam(Team team, int limit) {
        return summaryRepository
                .findByTeam_IdOrderByGeneratedAtDesc(team.getId(), PageRequest.of(0, limit))
                .stream().map(this::toDto).toList();
    }

    private void upsert(User user, Team team, MetricsSummaryDto dto) {
        Long userId = user != null ? user.getId() : null;
        Long teamId = team != null ? team.getId() : null;

        MetricSummaryEntity entity = summaryRepository
                .findByIdentity(userId, teamId, dto.getFrom(), dto.getTo(), dto.getScope(), dto.getContextRepoName())
                .orElseGet(MetricSummaryEntity::new);

        entity.setUser(user);
        entity.setTeam(team);
        entity.setPeriodFrom(dto.getFrom());
        entity.setPeriodTo(dto.getTo());
        entity.setScope(dto.getScope());
        entity.setContextRepoName(dto.getContextRepoName());
        entity.setHeadline(dto.getHeadline());
        entity.setOverview(dto.getOverview());
        entity.setModelName(dto.getModelName());
        entity.setRawModelOutput(dto.getRawModelOutput());
        entity.setGeneratedAt(Instant.now());

        try {
            entity.setInsights(objectMapper.writeValueAsString(dto.getInsights()));
            entity.setRecommendations(objectMapper.writeValueAsString(dto.getRecommendations()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize insights/recommendations userId={} teamId={}: {}",
                    userId, teamId, e.getMessage());
        }

        summaryRepository.save(entity);
        log.debug("Upserted summary: userId={}, teamId={}, scope={}, period={}/{}", userId, teamId,
                dto.getScope(), dto.getFrom(), dto.getTo());
    }

    MetricsSummaryDto toDto(MetricSummaryEntity entity) {
        List<MetricsSummaryDto.InsightDto> insights;
        List<String> recommendations;
        try {
            insights = entity.getInsights() != null
                    ? objectMapper.readValue(entity.getInsights(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, MetricsSummaryDto.InsightDto.class))
                    : List.of();
            recommendations = entity.getRecommendations() != null
                    ? objectMapper.readValue(entity.getRecommendations(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                    : List.of();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize insights/recommendations for entityId={}: {}", entity.getId(), e.getMessage());
            insights = List.of();
            recommendations = List.of();
        }

        return MetricsSummaryDto.builder()
                .from(entity.getPeriodFrom())
                .to(entity.getPeriodTo())
                .scope(entity.getScope())
                .contextRepoName(entity.getContextRepoName())
                .headline(entity.getHeadline())
                .overview(entity.getOverview())
                .insights(insights)
                .recommendations(recommendations)
                .rawModelOutput(entity.getRawModelOutput())
                .modelName(entity.getModelName())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }
}
