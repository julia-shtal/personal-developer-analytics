package com.juliashtal.devanalytics.auth;

import com.juliashtal.devanalytics.auth.model.AuthResponse;
import com.juliashtal.devanalytics.auth.model.RefreshToken;
import com.juliashtal.devanalytics.auth.model.request.LoginRequest;
import com.juliashtal.devanalytics.auth.model.request.RegisterRequest;
import com.juliashtal.devanalytics.auth.service.RefreshTokenService;
import com.juliashtal.devanalytics.invite.InviteService;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserCommitEmail;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers what {@link AuthServiceRegistrationRoleTest} does not: duplicate-account rejection,
 * commit-email seeding, login, token refresh, and logout.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCommitEmailRepository commitEmailRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private InviteService inviteService;

    @InjectMocks private AuthService authService;

    private RegisterRequest registerRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setUsername("someone");
        r.setEmail("someone@example.com");
        r.setPassword("correct horse battery staple");
        return r;
    }

    // ------------------------------------------------------------------
    // register
    // ------------------------------------------------------------------

    @Test
    void register_usernameAlreadyTaken_throwsAndNeverSaves() {
        when(userRepository.findByUsername("someone")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailAlreadyTaken_throwsAndNeverSaves() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("someone@example.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_blankInviteToken_neverValidatesOrRedeemsAnInvite() {
        RegisterRequest req = registerRequest();
        req.setInviteToken("   ");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(commitEmailRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(1L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        authService.register(req);

        verify(inviteService, never()).validateInvite(anyString());
        verify(inviteService, never()).redeemInvite(anyString(), any());
    }

    @Test
    void register_withInvite_redeemsItAfterSaving() {
        RegisterRequest req = registerRequest();
        req.setInviteToken("invite-token");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(commitEmailRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(inviteService.validateInvite("invite-token"))
                .thenReturn(new com.juliashtal.devanalytics.invite.model.InviteInfoDto(
                        "someone@example.com", "DEVELOPER", null));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });

        authService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(inviteService).redeemInvite(eq("invite-token"), captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(7L);
    }

    // ------------------------------------------------------------------
    // seedCommitEmail (private, exercised through register)
    // ------------------------------------------------------------------

    private void stubRegisterPrerequisites() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.count()).thenReturn(1L);
    }

    @Test
    void register_blankEmail_skipsSeedingACommitEmail() {
        RegisterRequest req = registerRequest();
        req.setEmail("  ");
        stubRegisterPrerequisites();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        authService.register(req);

        verify(commitEmailRepository, never()).findByEmail(anyString());
        verify(commitEmailRepository, never()).save(any());
    }

    @Test
    void register_emailAlreadyDeclaredByAnotherAccount_skipsSeedingWithoutFailing() {
        stubRegisterPrerequisites();
        when(commitEmailRepository.findByEmail("someone@example.com"))
                .thenReturn(Optional.of(new UserCommitEmail()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        authService.register(registerRequest());

        verify(commitEmailRepository, never()).save(any());
    }

    @Test
    void register_newEmail_seedsItAsTheFirstCommitEmailLowercasedAndTrimmed() {
        RegisterRequest req = registerRequest();
        req.setEmail("  Someone@Example.com  ");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.count()).thenReturn(1L);
        when(commitEmailRepository.findByEmail("someone@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        authService.register(req);

        ArgumentCaptor<UserCommitEmail> captor = ArgumentCaptor.forClass(UserCommitEmail.class);
        verify(commitEmailRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("someone@example.com");
    }

    // ------------------------------------------------------------------
    // login
    // ------------------------------------------------------------------

    @Test
    void login_validCredentials_returnsTokensFromTheAuthenticatedPrincipal() {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("someone");
        req.setPassword("correct horse battery staple");

        User user = new User();
        user.setId(1L);
        user.setUsername("someone");
        CustomUserDetails principal = new CustomUserDetails(user);

        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateAccessToken(principal)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900_000L);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);

        AuthResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
    }

    @Test
    void login_badCredentials_rewrapsWithAGenericMessage() {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("someone");
        req.setPassword("wrong");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("no such user: someone"));

        // The rewritten message must not leak whether the account exists.
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    // ------------------------------------------------------------------
    // refreshToken
    // ------------------------------------------------------------------

    @Test
    void refreshToken_valid_rotatesAndReturnsNewTokens() {
        User user = new User();
        user.setId(1L);
        user.setUsername("someone");

        RefreshToken oldToken = new RefreshToken();
        oldToken.setToken("old");
        RefreshToken newToken = new RefreshToken();
        newToken.setToken("new");
        newToken.setUser(user);

        when(refreshTokenService.verifyToken("old")).thenReturn(oldToken);
        when(refreshTokenService.rotateToken(oldToken)).thenReturn(newToken);
        when(jwtService.generateAccessToken(any(CustomUserDetails.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(60_000L);

        AuthResponse response = authService.refreshToken("old");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new");
        assertThat(response.getExpiresIn()).isEqualTo(60L);
    }

    // ------------------------------------------------------------------
    // logout
    // ------------------------------------------------------------------

    @Test
    void logout_revokesRefreshTokensAndBumpsTokenVersion() {
        authService.logout(1L);

        verify(refreshTokenService).revokeAllUserTokens(1L);
        verify(userRepository, times(1)).incrementTokenVersion(1L);
    }
}
