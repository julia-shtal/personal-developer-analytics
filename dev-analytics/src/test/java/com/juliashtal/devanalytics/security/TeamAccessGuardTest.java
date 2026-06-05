package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamAccessGuardTest {

    @Mock TeamRepository teamRepository;
    @Mock Authentication authentication;

    @InjectMocks TeamAccessGuard guard;

    private static final Long TEAM_ID = 42L;
    private static final Long USER_ID = 7L;

    private CustomUserDetails detailsFor(Role role) {
        User user = new User();
        user.setId(USER_ID);
        user.setRole(role);
        return new CustomUserDetails(user);
    }

    @BeforeEach
    void stubPrincipal() {
        // most tests use a real principal — individual tests override when needed
    }

    @Test
    void admin_alwaysGranted() {
        when(authentication.getPrincipal()).thenReturn(detailsFor(Role.ADMIN));
        assertThat(guard.canRead(TEAM_ID, authentication)).isTrue();
        verifyNoInteractions(teamRepository);
    }

    @Test
    void managerOfTeam_granted() {
        when(authentication.getPrincipal()).thenReturn(detailsFor(Role.MANAGER));
        when(teamRepository.existsByIdAndManagerId(TEAM_ID, USER_ID)).thenReturn(true);
        assertThat(guard.canRead(TEAM_ID, authentication)).isTrue();
    }

    @Test
    void memberOfTeam_granted() {
        when(authentication.getPrincipal()).thenReturn(detailsFor(Role.DEVELOPER));
        when(teamRepository.existsByIdAndManagerId(TEAM_ID, USER_ID)).thenReturn(false);
        when(teamRepository.existsByIdAndMembersId(TEAM_ID, USER_ID)).thenReturn(true);
        assertThat(guard.canRead(TEAM_ID, authentication)).isTrue();
    }

    @Test
    void nonMemberDeveloper_denied() {
        when(authentication.getPrincipal()).thenReturn(detailsFor(Role.DEVELOPER));
        when(teamRepository.existsByIdAndManagerId(TEAM_ID, USER_ID)).thenReturn(false);
        when(teamRepository.existsByIdAndMembersId(TEAM_ID, USER_ID)).thenReturn(false);
        assertThat(guard.canRead(TEAM_ID, authentication)).isFalse();
    }

    @Test
    void anonymous_denied() {
        assertThat(guard.canRead(TEAM_ID, null)).isFalse();
        verifyNoInteractions(teamRepository);
    }
}
