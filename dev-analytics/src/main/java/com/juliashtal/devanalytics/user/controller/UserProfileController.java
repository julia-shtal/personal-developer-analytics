package com.juliashtal.devanalytics.user.controller;

import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.CommitEmailDto;
import com.juliashtal.devanalytics.user.model.NotificationPrefsDto;
import com.juliashtal.devanalytics.user.model.UserSummary;
import com.juliashtal.devanalytics.user.model.request.AddCommitEmailRequest;
import com.juliashtal.devanalytics.user.model.request.ChangePasswordRequest;
import com.juliashtal.devanalytics.user.model.request.UpdateProfileRequest;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
import com.juliashtal.devanalytics.user.service.UserNotificationPrefsService;
import com.juliashtal.devanalytics.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the current user.
 * Mounted at /api/users/me — profile and notification preferences.
 */
@RestController
@RequestMapping("/api/users/me")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;
    private final UserNotificationPrefsService notificationPrefsService;
    private final AuthorIdentityService authorIdentityService;

    @Operation(summary = "Get the current user's profile")
    @GetMapping
    public ResponseEntity<UserSummary> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(UserSummary.from(userService.getById(userId)));
    }

    @Operation(summary = "Update the current user's profile")
    @PutMapping
    public ResponseEntity<UserSummary> updateProfile(@RequestBody UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(UserSummary.from(userService.updateProfile(userId, request)));
    }

    @Operation(summary = "Change the current user's password")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete the current user's account and all associated data.")
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount() {
        Long userId = SecurityUtils.getCurrentUserId();
        userService.deleteSelf(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List the addresses the current user commits with",
            description = "Commits are attributed to a user by these addresses, or by the "
                    + "GitHub account resolved for the commit. Local repositories can only "
                    + "match on an address, so every address used locally must be declared.")
    @GetMapping("/commit-emails")
    public ResponseEntity<List<CommitEmailDto>> listCommitEmails() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(authorIdentityService.listCommitEmails(userId));
    }

    @Operation(summary = "Declare an additional address the current user commits with")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Address added"),
            @ApiResponse(responseCode = "200", description = "The caller already declared this address"),
            @ApiResponse(responseCode = "400", description = "Malformed address"),
            @ApiResponse(responseCode = "409", description = "Address already linked to another account")
    })
    @PostMapping("/commit-emails")
    public ResponseEntity<CommitEmailDto> addCommitEmail(@RequestBody AddCommitEmailRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        AuthorIdentityService.AddCommitEmailResult result =
                authorIdentityService.addCommitEmail(userId, request.getEmail());
        // 200 rather than 201 when the caller already held the address, so a retry is not an error.
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.email());
    }

    @Operation(summary = "Remove one of the current user's declared commit addresses")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Address removed"),
            @ApiResponse(responseCode = "404", description = "Not one of the caller's addresses")
    })
    @DeleteMapping("/commit-emails/{id}")
    public ResponseEntity<Void> removeCommitEmail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        authorIdentityService.removeCommitEmail(userId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get notification preferences for the current user.")
    @GetMapping("/notifications")
    public ResponseEntity<NotificationPrefsDto> getNotifications() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(NotificationPrefsDto.from(notificationPrefsService.getOrCreate(userId)));
    }

    @Operation(summary = "Update notification preferences for the current user.")
    @PutMapping("/notifications")
    public ResponseEntity<NotificationPrefsDto> updateNotifications(@RequestBody NotificationPrefsDto dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(notificationPrefsService.update(userId, dto));
    }
}