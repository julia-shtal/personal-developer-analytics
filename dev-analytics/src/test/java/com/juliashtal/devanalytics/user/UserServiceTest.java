package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.request.UpdateProfileRequest;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService service;

    @Test
    void getReferenceById_delegatesToRepositoryReference() {
        User reference = new User();
        reference.setId(7L);
        when(repository.getReferenceById(7L)).thenReturn(reference);

        User result = service.getReferenceById(7L);

        assertThat(result).isSameAs(reference);
    }

    @Test
    void getById_existingId_returnsUser() {
        User user = new User();
        user.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(service.getById(1L)).isSameAs(user);
    }

    @Test
    void getById_missingId_throwsNoSuchElement() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateProfile_appliesProvidedFields() {
        User user = new User();
        user.setId(1L);
        user.setUsername("old");
        user.setEmail("old@example.com");
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("newname");
        req.setEmail("new@example.com");
        req.setTimezone("Europe/Berlin");
        req.setGithubLogin("octocat");

        User result = service.updateProfile(1L, req);

        assertThat(result.getUsername()).isEqualTo("newname");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getTimezone()).isEqualTo("Europe/Berlin");
        assertThat(result.getGithubLogin()).isEqualTo("octocat");
    }

    @Test
    void updateProfile_blankEmail_keepsExistingEmail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("keep@example.com");
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail("  ");

        User result = service.updateProfile(1L, req);

        assertThat(result.getEmail()).isEqualTo("keep@example.com");
    }

    @Test
    void changePassword_correctOldPassword_updatesHash() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash("oldHash");
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "oldHash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword123")).thenReturn("newHash");

        service.changePassword(1L, "oldPass", "newPassword123");

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
        verify(repository).save(user);
    }

    @Test
    void changePassword_incorrectOldPassword_throwsBadRequest() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash("oldHash");
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "oldHash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(1L, "wrongPass", "newPassword123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void changePassword_newPasswordTooShort_throwsBadRequest() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash("oldHash");
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "oldHash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(1L, "oldPass", "short"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("8 characters");
    }

    @Test
    void updateRole_existingUser_updatesAndReturns() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.DEVELOPER);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        User result = service.updateRole(1L, Role.MANAGER);

        assertThat(result.getRole()).isEqualTo(Role.MANAGER);
    }

    @Test
    void findAll_returnsAllUsers() {
        List<User> users = List.of(new User(), new User());
        when(repository.findAll()).thenReturn(users);

        assertThat(service.findAll()).isEqualTo(users);
    }

    @Test
    void search_blankQuery_returnsAllUsers() {
        List<User> users = List.of(new User());
        when(repository.findAll()).thenReturn(users);

        assertThat(service.search("  ")).isEqualTo(users);
    }

    @Test
    void search_nonBlankQuery_delegatesToSearchByEmailOrUsername() {
        List<User> users = List.of(new User());
        when(repository.searchByEmailOrUsername("bob")).thenReturn(users);

        assertThat(service.search(" bob ")).isEqualTo(users);
    }

    @Test
    void delete_existingUser_deletesById() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_missingUser_throwsNoSuchElement() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(NoSuchElementException.class);

        verify(repository, never()).deleteById(any());
    }

    @Test
    void touchLastActive_delegatesToRepository() {
        service.touchLastActive(1L);

        verify(repository).touchLastActive(1L);
    }
}
