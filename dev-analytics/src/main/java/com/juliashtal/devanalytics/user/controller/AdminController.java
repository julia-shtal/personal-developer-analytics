package com.juliashtal.devanalytics.user.controller;

import com.juliashtal.devanalytics.invite.CreateInviteRequest;
import com.juliashtal.devanalytics.invite.InviteService;
import com.juliashtal.devanalytics.invite.InviteTokenDto;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.user.model.AdminStatsDto;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.request.UpdateRoleRequest;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserSummary;
import com.juliashtal.devanalytics.user.service.AdminService;
import com.juliashtal.devanalytics.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for administration. Mounted at /api/admin — user management and platform stats (ADMIN only).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AdminService adminService;
    private final InviteService inviteService;
    private final CheckHelper checkHelper;

    @Operation(summary = "Active users in last 24h, database size, and AI calls today")
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        return ResponseEntity.ok(new AdminStatsDto(
                adminService.activeUsersLast24h(),
                adminService.databaseSizeBytes(),
                adminService.aiCallsToday()
        ));
    }

    @Operation(summary = "List all users. Optionally filter by email/username with ?q=")
    @GetMapping("/users")
    public ResponseEntity<List<UserSummary>> getAllUsers(@RequestParam(required = false) String q) {
        List<UserSummary> users = userService.search(q).stream()
                .map(UserSummary::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Change a user's role")
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<UserSummary> updateUserRole(
            @PathVariable Long userId,
            @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(UserSummary.from(userService.updateRole(userId, request.getRole())));
    }

    @Operation(summary = "Delete a user account")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        userService.delete(userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Create an invite token; returns the invite URL for the admin to share manually")
    @PostMapping("/invites")
    public ResponseEntity<InviteTokenDto> createInvite(@RequestBody CreateInviteRequest req) {
        User admin = checkHelper.currentUser();
        Role role = req.getRole() != null ? Role.valueOf(req.getRole().toUpperCase()) : Role.DEVELOPER;
        InviteTokenDto dto = inviteService.createInvite(admin, req.getEmail(), role, req.getTeamId());
        return ResponseEntity.status(201).body(dto);
    }
}
