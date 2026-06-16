package com.juliashtal.devanalytics.user.controller;

import com.juliashtal.devanalytics.user.model.UserSummary;
import com.juliashtal.devanalytics.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * List all users — accessible to MANAGER and ADMIN so they can pick
     * team members without needing the full admin panel.
     */
    @Operation(summary = "List all users (manager/admin only, for team member selection)")
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<UserSummary>> listAll() {
        List<UserSummary> users = userService.findAll().stream()
                .map(UserSummary::from)
                .toList();
        return ResponseEntity.ok(users);
    }
}
