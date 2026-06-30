package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.model.GoalDto;
import com.juliashtal.devanalytics.ai.model.GoalEntity;
import com.juliashtal.devanalytics.ai.model.GoalRequestDto;
import com.juliashtal.devanalytics.ai.repository.GoalRepository;
import com.juliashtal.devanalytics.ai.service.GoalService;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.exception.NotFoundException;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock GoalRepository goalRepository;
    @InjectMocks GoalService service;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("alice");
    }

    private GoalEntity savedEntity(Long id, User user, String metricType,
                                   double target, LocalDate targetDate) {
        GoalEntity e = new GoalEntity();
        e.setId(id);
        e.setUser(user);
        e.setMetricType(metricType);
        e.setTargetValue(target);
        e.setTargetDate(targetDate);
        e.setCreatedAt(Instant.now());
        return e;
    }

    @Test
    void createGoal_validRequest_returnsGoalDto() {
        GoalRequestDto req = new GoalRequestDto("DAILY_COMMITS_COUNT", 5.0, LocalDate.of(2026, 7, 31));
        GoalEntity persisted = savedEntity(10L, owner, "DAILY_COMMITS_COUNT", 5.0, LocalDate.of(2026, 7, 31));
        when(goalRepository.save(any())).thenReturn(persisted);

        GoalDto result = service.createGoal(owner, req);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.metricType()).isEqualTo("DAILY_COMMITS_COUNT");
        assertThat(result.targetValue()).isEqualTo(5.0);
    }

    @Test
    void createGoal_invalidMetricType_throwsBadRequest() {
        GoalRequestDto req = new GoalRequestDto("NONEXISTENT_METRIC", 5.0, LocalDate.of(2026, 7, 31));

        assertThatThrownBy(() -> service.createGoal(owner, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("NONEXISTENT_METRIC");
    }

    @Test
    void deleteGoal_notOwner_throwsForbidden() {
        User otherUser = new User();
        otherUser.setId(99L);

        GoalEntity goal = savedEntity(5L, otherUser, "DAILY_COMMITS_COUNT", 3.0, LocalDate.of(2026, 7, 31));
        when(goalRepository.findById(5L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> service.deleteGoal(owner, 5L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteGoal_owner_deletesSuccessfully() {
        GoalEntity goal = savedEntity(5L, owner, "DAILY_COMMITS_COUNT", 3.0, LocalDate.of(2026, 7, 31));
        when(goalRepository.findById(5L)).thenReturn(Optional.of(goal));

        service.deleteGoal(owner, 5L);

        verify(goalRepository).delete(goal);
    }

    @Test
    void deleteGoal_unknownId_throwsNotFound() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteGoal(owner, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getGoals_pastTargetDate_notIncluded() {
        when(goalRepository.findByUser_IdOrderByTargetDateAsc(1L)).thenReturn(List.of());

        List<GoalDto> result = service.getGoals(owner);

        assertThat(result).isEmpty();
        verify(goalRepository).findByUser_IdOrderByTargetDateAsc(1L);
    }
}
