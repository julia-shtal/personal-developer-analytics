package com.juliashtal.devanalytics.user.controller;

import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.service.AvatarService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    /** Upload a custom avatar (JPEG / PNG / WebP, max 2 MB). Resized to 256×256 JPEG. */
    @Operation(summary = "Upload a custom avatar (JPEG/PNG/WebP, max 2MB), resized to 256x256 JPEG")
    @PostMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        avatarService.upload(SecurityUtils.getCurrentUserId(), file);
        return ResponseEntity.noContent().build();
    }

    /**
     * Serve a user's custom avatar image.
     * Intentionally unauthenticated so avatars render in team/admin views without re-auth.
     * Returns 404 if the user has no custom upload (use preset SVG or initials instead).
     */
    @Operation(summary = "Serve a user's avatar image (unauthenticated; 404 if no custom upload)")
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long id, WebRequest request) {
        return avatarService.getAvatarResponse(id, request);
    }

    /** Select one of the built-in preset SVGs by ID (e.g. "preset-03"). */
    @Operation(summary = "Select a built-in preset avatar by ID")
    @PutMapping("/me/avatar/preset/{presetId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> setPreset(@PathVariable String presetId) {
        avatarService.setPreset(SecurityUtils.getCurrentUserId(), presetId);
        return ResponseEntity.noContent().build();
    }

    /** Remove custom avatar and preset — falls back to initials in the UI. */
    @Operation(summary = "Remove the current user's custom avatar and preset selection")
    @DeleteMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAvatar() {
        avatarService.deleteAvatar(SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /** List available preset IDs. */
    @Operation(summary = "List available preset avatar IDs")
    @GetMapping("/avatar/presets")
    public List<String> getPresets() {
        return AvatarService.PRESET_IDS;
    }
}
