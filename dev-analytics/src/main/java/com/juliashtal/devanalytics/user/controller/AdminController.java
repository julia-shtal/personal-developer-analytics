package com.juliashtal.devanalytics.user.controller;

import com.juliashtal.devanalytics.user.model.request.UpdateRoleRequest;
import com.juliashtal.devanalytics.user.model.UserSummary;
import com.juliashtal.devanalytics.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @Operation(summary = "List all users. Optionally filter by email/username with ?q=")
    @GetMapping("/users")
    public ResponseEntity<List<UserSummary>> getAllUsers(@RequestParam(required = false) String q) {
        List<UserSummary> users = userService.search(q).stream()
                .map(UserSummary::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<UserSummary> updateUserRole(
            @PathVariable Long userId,
            @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(UserSummary.from(userService.updateRole(userId, request.getRole())));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        userService.delete(userId);
        return ResponseEntity.ok().build();
    }
}
