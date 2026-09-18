package com.juliashtal.devanalytics.auth;

import com.juliashtal.devanalytics.auth.model.request.RegisterRequest;
import com.juliashtal.devanalytics.auth.service.RefreshTokenService;
import com.juliashtal.devanalytics.invite.InviteService;
import com.juliashtal.devanalytics.invite.model.InviteInfoDto;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserCommitEmailRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Pins which role a registration grants.
 * <p>Every role-granting endpoint is behind {@code hasRole('ADMIN')}, so the first account on an
 * empty instance has to become the administrator or an operator can never reach the first one.
 * The predicate is "no users at all", never "no administrator": on a populated instance,
 * registering must not be a route to the role.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceRegistrationRoleTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCommitEmailRepository commitEmailRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private InviteService inviteService;

    @InjectMocks private AuthService authService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest();
        request.setUsername("someone");
        request.setEmail("someone@example.com");
        request.setPassword("correct horse battery staple");

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(commitEmailRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
    }

    private Role registeredRole() {
        authService.register(request);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(captor.capture());
        return captor.getValue().getRole();
    }

    @Test
    void register_emptyInstance_firstAccountBecomesAdmin() {
        when(userRepository.count()).thenReturn(0L);

        assertThat(registeredRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void register_instanceAlreadyHasUsers_defaultsToDeveloper() {
        when(userRepository.count()).thenReturn(1L);

        assertThat(registeredRole()).isEqualTo(Role.DEVELOPER);
    }

    @Test
    void register_withInviteOnEmptyInstance_usesInviteRoleNotAdmin() {
        request.setInviteToken("invite-token");
        when(inviteService.validateInvite("invite-token"))
                .thenReturn(new InviteInfoDto("someone@example.com", Role.MANAGER.name(), null));
        when(userRepository.count()).thenReturn(0L);

        assertThat(registeredRole()).isEqualTo(Role.MANAGER);
    }
}
