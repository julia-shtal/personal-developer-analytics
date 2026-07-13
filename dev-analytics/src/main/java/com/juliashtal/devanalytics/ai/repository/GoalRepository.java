package com.juliashtal.devanalytics.ai.repository;

import com.juliashtal.devanalytics.ai.model.GoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data repository for GoalEntity (goals). Finds a user's still-active goals for AI context.
 */
public interface GoalRepository extends JpaRepository<GoalEntity, Long> {

    /**
     * Returns all goals for the given user whose target date is on or after {@code today}.
     * Used by {@code AiContextBuilderService} to include only active goals in AI context.
     */
    List<GoalEntity> findByUser_IdAndTargetDateGreaterThanEqual(Long userId, LocalDate today);

    /** Returns all goals for the given user regardless of target date (for the goals list UI). */
    List<GoalEntity> findByUser_IdOrderByTargetDateAsc(Long userId);
}
