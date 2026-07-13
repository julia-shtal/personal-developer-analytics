package com.juliashtal.devanalytics.ai.service;

import com.juliashtal.devanalytics.ai.model.GoalDto;
import com.juliashtal.devanalytics.ai.model.GoalEntity;
import com.juliashtal.devanalytics.ai.model.GoalRequestDto;
import com.juliashtal.devanalytics.ai.repository.GoalRepository;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates and validates per-metric user goals.
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;

    @Transactional
    public GoalDto createGoal(User owner, GoalRequestDto request) {
        validateMetricType(request.metricType());
        if (request.targetValue() < 0) {
            throw new BadRequestException("targetValue must be non-negative");
        }

        GoalEntity entity = new GoalEntity();
        entity.setUser(owner);
        entity.setMetricType(request.metricType());
        entity.setTargetValue(request.targetValue());
        entity.setTargetDate(request.targetDate());

        GoalEntity saved = goalRepository.save(entity);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<GoalDto> getGoals(User owner) {
        return goalRepository.findByUser_IdOrderByTargetDateAsc(owner.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public void deleteGoal(User requestingUser, Long goalId) {
        GoalEntity goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new NotFoundException("Goal not found: " + goalId));
        if (!goal.getUser().getId().equals(requestingUser.getId())) {
            throw new ForbiddenException("Goal does not belong to the requesting user");
        }
        goalRepository.delete(goal);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateMetricType(String name) {
        try {
            MetricType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown metricType: " + name);
        }
    }

    private GoalDto toDto(GoalEntity entity) {
        return new GoalDto(
                entity.getId(),
                entity.getMetricType(),
                entity.getTargetValue(),
                entity.getTargetDate(),
                entity.getCreatedAt());
    }
}
