package com.juliashtal.devanalytics.ai.controller;

import com.juliashtal.devanalytics.ai.model.GoalDto;
import com.juliashtal.devanalytics.ai.model.GoalRequestDto;
import com.juliashtal.devanalytics.ai.service.GoalService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.User;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final CheckHelper checkHelper;

    @Operation(summary = "Create a new metric goal for the current user")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalDto createGoal(@RequestBody GoalRequestDto request) {
        User user = checkHelper.currentUser();
        return goalService.createGoal(user, request);
    }

    @Operation(summary = "List all goals for the current user")
    @GetMapping
    public List<GoalDto> getGoals() {
        User user = checkHelper.currentUser();
        return goalService.getGoals(user);
    }

    @Operation(summary = "Delete a goal by ID (owner only)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGoal(@PathVariable Long id) {
        User user = checkHelper.currentUser();
        goalService.deleteGoal(user, id);
    }
}
