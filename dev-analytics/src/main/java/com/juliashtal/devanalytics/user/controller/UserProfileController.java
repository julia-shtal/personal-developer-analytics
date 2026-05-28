package com.juliashtal.devanalytics.user.controller;

import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.NotificationPrefsDto;
import com.juliashtal.devanalytics.user.model.UserSummary;
import com.juliashtal.devanalytics.user.model.request.ChangePasswordRequest;
import com.juliashtal.devanalytics.user.model.request.UpdateProfileRequest;
import com.juliashtal.devanalytics.user.service.UserNotificationPrefsService;
import com.juliashtal.devanalytics.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;
    private final UserNotificationPrefsService notificationPrefsService;

    @GetMapping
    public ResponseEntity<UserSummary> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(UserSummary.from(userService.getById(userId)));
    }

    @PutMapping
    public ResponseEntity<UserSummary> updateProfile(@RequestBody UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(UserSummary.from(userService.updateProfile(userId, request)));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
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