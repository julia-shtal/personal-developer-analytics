package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceDeleteTest {

    @Mock UserRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService service;

    private User userWithRole(Long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    @Test
    void deleteSelf_asLastAdmin_throwsConflict() {
        User admin = userWithRole(1L, Role.ADMIN);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteSelf(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last admin");

        verify(repository, never()).deleteById(any());
    }

    @Test
    void deleteSelf_asAdminWithOtherAdmins_succeeds() {
        User admin = userWithRole(1L, Role.ADMIN);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.countByRole(Role.ADMIN)).thenReturn(2L);

        service.deleteSelf(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void deleteSelf_asRegularUser_succeeds() {
        User dev = userWithRole(2L, Role.DEVELOPER);
        when(repository.findById(2L)).thenReturn(Optional.of(dev));

        service.deleteSelf(2L);

        verify(repository).deleteById(2L);
        verify(repository, never()).countByRole(any());
    }

    @Test
    void deleteSelf_asManager_succeeds() {
        User manager = userWithRole(3L, Role.MANAGER);
        when(repository.findById(3L)).thenReturn(Optional.of(manager));

        service.deleteSelf(3L);

        verify(repository).deleteById(3L);
    }
}