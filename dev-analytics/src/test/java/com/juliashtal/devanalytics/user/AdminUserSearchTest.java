package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserSearchTest {

    @Mock UserRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService service;

    @Test
    void search_withQuery_delegatesToRepository() {
        User u = new User();
        u.setEmail("alice@example.com");
        when(repository.searchByEmailOrUsername("alice")).thenReturn(List.of(u));

        List<User> result = service.search("alice");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("alice@example.com");
        verify(repository).searchByEmailOrUsername("alice");
        verify(repository, never()).findAll();
    }

    @Test
    void search_withNullQuery_returnsAll() {
        when(repository.findAll()).thenReturn(List.of(new User(), new User()));

        List<User> result = service.search(null);

        assertThat(result).hasSize(2);
        verify(repository).findAll();
        verify(repository, never()).searchByEmailOrUsername(any());
    }

    @Test
    void search_withBlankQuery_returnsAll() {
        when(repository.findAll()).thenReturn(List.of(new User()));

        List<User> result = service.search("  ");

        assertThat(result).hasSize(1);
        verify(repository).findAll();
    }
}