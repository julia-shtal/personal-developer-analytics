package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.AvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.WebRequest;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock UserRepository userRepository;
    @Mock WebRequest webRequest;
    @InjectMocks AvatarService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
    }

    // ── upload (validation branches) ────────────────────────────────────────

    @Test
    void upload_unsupportedContentType_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("avatar", "a.gif", "image/gif", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG, PNG, or WebP");
    }

    @Test
    void upload_fileTooLarge_throwsIllegalArgument() {
        byte[] tooLarge = new byte[(int) (2L * 1024 * 1024 + 1)];
        MockMultipartFile file = new MockMultipartFile("avatar", "a.png", "image/png", tooLarge);

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
    }

    // ── getAvatarResponse ────────────────────────────────────────────────────

    @Test
    void getAvatarResponse_userNotFound_throwsNoSuchElement() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAvatarResponse(99L, webRequest))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getAvatarResponse_noAvatarData_returnsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ResponseEntity<byte[]> response = service.getAvatarResponse(1L, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAvatarResponse_notModified_returns304() {
        user.setAvatarData(new byte[]{1, 2, 3});
        user.setAvatarContentType("image/jpeg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(webRequest.checkNotModified(anyString())).thenReturn(true);

        ResponseEntity<byte[]> response = service.getAvatarResponse(1L, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void getAvatarResponse_modified_returnsBodyWithCacheHeaders() {
        byte[] data = {1, 2, 3, 4};
        user.setAvatarData(data);
        user.setAvatarContentType("image/jpeg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(webRequest.checkNotModified(anyString())).thenReturn(false);

        ResponseEntity<byte[]> response = service.getAvatarResponse(1L, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(data);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("public, max-age=3600");
    }

    // ── setPreset ────────────────────────────────────────────────────────────

    @Test
    void setPreset_unknownPreset_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.setPreset(1L, "preset-99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preset-99");
    }

    @Test
    void setPreset_knownPreset_updatesUserAndClearsCustomAvatar() {
        user.setAvatarData(new byte[]{1});
        user.setAvatarContentType("image/png");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.setPreset(1L, "preset-03");

        assertThat(user.getAvatarPreset()).isEqualTo("preset-03");
        assertThat(user.getAvatarData()).isNull();
        assertThat(user.getAvatarContentType()).isNull();
        verify(userRepository).save(user);
    }

    // ── deleteAvatar ─────────────────────────────────────────────────────────

    @Test
    void deleteAvatar_clearsAllAvatarFields() {
        user.setAvatarData(new byte[]{1});
        user.setAvatarContentType("image/png");
        user.setAvatarPreset("preset-01");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.deleteAvatar(1L);

        assertThat(user.getAvatarData()).isNull();
        assertThat(user.getAvatarContentType()).isNull();
        assertThat(user.getAvatarPreset()).isNull();
        verify(userRepository).save(user);
    }
}
