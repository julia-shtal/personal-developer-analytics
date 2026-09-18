package com.juliashtal.devanalytics.security.service;

import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pins the exception type an unresolvable identifier produces.
 * <p>Only {@code UsernameNotFoundException} reaches the authentication provider's hidden-user
 * handling, which answers an unknown identifier and a wrong password alike with 401. Any other
 * type is wrapped as an internal error and answers 500, which tells a caller whether the
 * account exists.</p>
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_knownUsername_returnsDetails() {
        User user = new User();
        user.setId(1L);
        user.setUsername("someone");
        when(userRepository.findByUsername("someone")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("someone");

        assertThat(details.getUsername()).isEqualTo("someone");
    }

    @Test
    void loadUserByUsername_knownEmail_fallsBackToEmailLookup() {
        User user = new User();
        user.setId(1L);
        user.setUsername("someone");
        when(userRepository.findByUsername("someone@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("someone@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("someone@example.com");

        assertThat(details.getUsername()).isEqualTo("someone");
    }

    @Test
    void loadUserByUsername_unknownIdentifier_throwsUsernameNotFound() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_nullIdentifier_throwsUsernameNotFound() {
        when(userRepository.findByUsername(null)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername(null))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
