package com.juliashtal.devanalytics.invite;

import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock InviteTokenRepository inviteTokenRepository;
    @Mock TeamRepository teamRepository;

    InviteService service;

    @BeforeEach
    void setUp() {
        service = new InviteService(inviteTokenRepository, teamRepository);
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
    }

    @Test
    void createInvite_sets7DayExpiry() {
        User admin = new User();
        admin.setId(1L);

        when(inviteTokenRepository.save(any())).thenAnswer(inv -> {
            InviteTokenEntity e = inv.getArgument(0);
            e.setToken("test-token");
            return e;
        });

        Instant before = Instant.now();
        InviteTokenDto dto = service.createInvite(admin, "alice@example.com", Role.DEVELOPER, null);
        Instant after = Instant.now();

        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.role()).isEqualTo("DEVELOPER");
        assertThat(dto.inviteUrl()).contains("/register?invite=test-token");
        assertThat(dto.expiresAt()).isAfter(before.plus(6, ChronoUnit.DAYS));
        assertThat(dto.expiresAt()).isBefore(after.plus(8, ChronoUnit.DAYS));
    }

    @Test
    void validateInvite_expiredToken_throws() {
        InviteTokenEntity expired = buildInvite(null);
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(inviteTokenRepository.findByToken("tok")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.validateInvite("tok"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateInvite_usedToken_throws() {
        InviteTokenEntity used = buildInvite(null);
        used.setUsedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(inviteTokenRepository.findByToken("tok")).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.validateInvite("tok"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void redeemInvite_addsUserToTeam() {
        Team team = new Team();
        team.setId(42L);
        team.setName("Alpha");
        team.setMembers(new HashSet<>());

        InviteTokenEntity invite = buildInvite(team);
        when(inviteTokenRepository.findByToken("tok")).thenReturn(Optional.of(invite));
        when(teamRepository.findById(42L)).thenReturn(Optional.of(team));
        when(inviteTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User newUser = new User();
        newUser.setId(99L);
        service.redeemInvite("tok", newUser);

        assertThat(team.getMembers()).contains(newUser);
        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(teamCaptor.capture());
        assertThat(teamCaptor.getValue().getMembers()).contains(newUser);
    }

    @Test
    void redeemInvite_noTeam_onlyMarksUsed() {
        InviteTokenEntity invite = buildInvite(null);
        when(inviteTokenRepository.findByToken("tok")).thenReturn(Optional.of(invite));
        when(inviteTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.redeemInvite("tok", new User());

        verify(teamRepository, never()).findById(any());
        verify(teamRepository, never()).save(any());
    }

    private InviteTokenEntity buildInvite(Team team) {
        InviteTokenEntity e = new InviteTokenEntity();
        e.setToken("tok");
        e.setEmail("alice@example.com");
        e.setRole(Role.DEVELOPER);
        e.setTeam(team);
        e.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        return e;
    }
}
