package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Stores and serves user avatars, validating and resizing uploads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarService {

    private static final long MAX_BYTES = 2L * 1024 * 1024; // 2 MB
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final List<String> PRESET_IDS = List.of(
            "preset-01", "preset-02", "preset-03", "preset-04",
            "preset-05", "preset-06", "preset-07", "preset-08",
            "preset-09", "preset-10", "preset-11", "preset-12"
    );

    private final UserRepository userRepository;

    @Transactional
    public void upload(Long userId, MultipartFile file) throws IOException {
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct)) {
            throw new IllegalArgumentException("Only JPEG, PNG, or WebP images are accepted");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Image must be under 2 MB");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .size(256, 256)
                .outputFormat("jpg")
                .outputQuality(0.85)
                .toOutputStream(out);

        User user = getUser(userId);
        user.setAvatarData(out.toByteArray());
        user.setAvatarContentType("image/jpeg");
        user.setAvatarPreset(null);
        userRepository.save(user);
        log.debug("Avatar uploaded: userId={}, storedBytes={}", userId, out.size());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getAvatarResponse(Long userId, WebRequest request) {
        User user = getUser(userId);
        if (user.getAvatarData() == null) {
            return ResponseEntity.notFound().build();
        }

        // Weak ETag based on hash of first 64 bytes + array length — cheap, no full read
        String etag = '"' + Integer.toHexString(Arrays.hashCode(user.getAvatarData())) + '"';
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(user.getAvatarContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .eTag(etag)
                .body(user.getAvatarData());
    }

    @Transactional
    public void setPreset(Long userId, String presetId) {
        if (!PRESET_IDS.contains(presetId)) {
            throw new IllegalArgumentException("Unknown preset: " + presetId);
        }
        User user = getUser(userId);
        user.setAvatarPreset(presetId);
        user.setAvatarData(null);
        user.setAvatarContentType(null);
        userRepository.save(user);
    }

    @Transactional
    public void deleteAvatar(Long userId) {
        User user = getUser(userId);
        user.setAvatarData(null);
        user.setAvatarContentType(null);
        user.setAvatarPreset(null);
        userRepository.save(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }
}
