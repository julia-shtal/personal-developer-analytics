package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.config.SecurityConfig;
import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.security.JwtAuthFilter;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.controller.AvatarController;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.AvatarService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests for {@link AvatarController}.
 *
 * <p>Two routes here are deliberately public so avatars render in team and admin views without
 * re-auth; the rest write to the caller's own row. That asymmetry is the contract worth pinning,
 * and only the real filter chain can show it.</p>
 *
 * <p>The public matcher {@code /api/users/*&#47;avatar} carries no HTTP method and its wildcard
 * matches the literal {@code me}, so the two write routes pass the path rule and are stopped by
 * their method annotation instead — refused, but as 403 where the rest of the API answers 401.
 * These tests record that, they do not endorse it.</p>
 */
@WebMvcTest(AvatarController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AvatarControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AvatarService avatarService;
    // Required by SecurityConfig / JwtAuthFilter when filters are active
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;
    // Required by ActivityInterceptor (HandlerInterceptor picked up by @WebMvcTest)
    @MockBean UserService userService;

    private static CustomUserDetails principal(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("alice");
        u.setRole(Role.DEVELOPER);
        return new CustomUserDetails(u);
    }

    @Test
    void uploadAvatar_authenticated_storesAgainstTheCallersId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "me.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/users/me/avatar").file(file).with(user(principal(7L))))
                .andExpect(status().isNoContent());

        verify(avatarService).upload(org.mockito.ArgumentMatchers.eq(7L), any());
    }

    @Test
    void uploadAvatar_rejectedByService_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "me.gif", MediaType.IMAGE_GIF_VALUE, new byte[]{1});
        doThrow(new BadRequestException("Unsupported image type"))
                .when(avatarService).upload(any(), any());

        mockMvc.perform(multipart("/api/users/me/avatar").file(file).with(user(principal(7L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAvatar_unauthenticated_isRefusedButWith403Not401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "me.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/api/users/me/avatar").file(file))
                .andExpect(status().isForbidden());

        verify(avatarService, never()).upload(any(), any());
    }

    @Test
    void getAvatar_unauthenticated_isServedSoImagesRenderWithoutAuth() throws Exception {
        when(avatarService.getAvatarResponse(org.mockito.ArgumentMatchers.eq(8L), any()))
                .thenReturn(ResponseEntity.ok(new byte[]{1, 2, 3}));

        mockMvc.perform(get("/api/users/8/avatar"))
                .andExpect(status().isOk());
    }

    @Test
    void getPresets_unauthenticated_listsTheBuiltInIds() throws Exception {
        mockMvc.perform(get("/api/users/avatar/presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(AvatarService.PRESET_IDS.get(0)));
    }

    @Test
    void setPreset_authenticated_returns204() throws Exception {
        mockMvc.perform(put("/api/users/me/avatar/preset/preset-03").with(user(principal(7L))))
                .andExpect(status().isNoContent());

        verify(avatarService).setPreset(7L, "preset-03");
    }

    @Test
    void setPreset_unknownId_returns400() throws Exception {
        doThrow(new BadRequestException("Unknown preset"))
                .when(avatarService).setPreset(7L, "preset-99");

        mockMvc.perform(put("/api/users/me/avatar/preset/preset-99").with(user(principal(7L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setPreset_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/users/me/avatar/preset/preset-03"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAvatar_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/me/avatar").with(user(principal(7L))))
                .andExpect(status().isNoContent());

        verify(avatarService).deleteAvatar(7L);
    }

    @Test
    void deleteAvatar_unauthenticated_isRefusedButWith403Not401() throws Exception {
        mockMvc.perform(delete("/api/users/me/avatar"))
                .andExpect(status().isForbidden());

        verify(avatarService, never()).deleteAvatar(any());
    }
}
