package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.notification.NotificationDispatchService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.TeamDto;
import com.juliashtal.devanalytics.user.model.TeamMembershipDto;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.request.TeamConfigRequest;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.user.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock UserRepository userRepository;
    @Mock DataSourceConfigRepository dataSourceConfigRepository;
    @Mock NotificationDispatchService notificationDispatch;
    @InjectMocks TeamService service;

    private User user(Long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        u.setEmail("user" + id + "@example.com");
        u.setRole(role);
        return u;
    }

    private Team team(Long id, User manager) {
        Team team = new Team();
        team.setId(id);
        team.setName("Team " + id);
        team.setManager(manager);
        return team;
    }

    private MockedStatic<SecurityUtils> mockCurrentUser(Long userId) {
        MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class);
        su.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
        return su;
    }

    // =========================================================================
    // createTeam
    // =========================================================================

    @Test
    void createTeam_validName_savesAndReturnsDto() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            User manager = user(1L, Role.MANAGER);
            when(userRepository.findById(1L)).thenReturn(Optional.of(manager));
            when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
                Team t = inv.getArgument(0);
                t.setId(100L);
                return t;
            });

            TeamDto result = service.createTeam("Backend Guild");

            assertThat(result.id()).isEqualTo(100L);
            assertThat(result.name()).isEqualTo("Backend Guild");
            assertThat(result.manager().getId()).isEqualTo(1L);
        }
    }

    @Test
    void createTeam_userNotFound_throwsNoSuchElement() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createTeam("Backend Guild"))
                    .isInstanceOf(NoSuchElementException.class);

            verify(teamRepository, never()).save(any());
        }
    }

    // =========================================================================
    // getMyTeams
    // =========================================================================

    @Test
    void getMyTeams_returnsOnlyNonArchivedManagedTeams() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            User manager = user(1L, Role.MANAGER);
            Team active = team(10L, manager);
            Team archived = team(11L, manager);
            archived.setArchivedAt(Instant.now());

            when(teamRepository.findByManagerId(1L)).thenReturn(List.of(active, archived));

            List<TeamDto> result = service.getMyTeams();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(10L);
        }
    }

    // =========================================================================
    // getMyMemberships
    // =========================================================================

    @Test
    void getMyMemberships_returnsOnlyNonArchivedMemberTeams() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            User manager = user(1L, Role.MANAGER);
            Team active = team(10L, manager);
            Team archived = team(11L, manager);
            archived.setArchivedAt(Instant.now());

            when(teamRepository.findByMembersId(2L)).thenReturn(List.of(active, archived));

            List<TeamMembershipDto> result = service.getMyMemberships();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(10L);
        }
    }

    // =========================================================================
    // addMember
    // =========================================================================

    @Test
    void addMember_asManager_addsMemberAndNotifies() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            User manager = user(1L, Role.MANAGER);
            User newMember = user(2L, Role.DEVELOPER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.findById(2L)).thenReturn(Optional.of(newMember));
            when(teamRepository.save(team)).thenReturn(team);

            TeamDto result = service.addMember(10L, 2L);

            assertThat(team.getMembers()).contains(newMember);
            assertThat(result.members()).extracting("id").contains(2L);
            verify(notificationDispatch).sendNewTeamMemberIfEnabled(newMember, team.getName());
        }
    }

    @Test
    void addMember_notManager_throwsForbidden() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            User manager = user(1L, Role.MANAGER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));

            assertThatThrownBy(() -> service.addMember(10L, 3L))
                    .isInstanceOf(ForbiddenException.class);

            verify(teamRepository, never()).save(any());
        }
    }

    @Test
    void addMember_teamNotFound_throwsNoSuchElement() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            when(teamRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addMember(10L, 2L))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void addMember_userNotFound_throwsNoSuchElement() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            User manager = user(1L, Role.MANAGER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addMember(10L, 2L))
                    .isInstanceOf(NoSuchElementException.class);

            verify(teamRepository, never()).save(any());
        }
    }

    // =========================================================================
    // removeMember
    // =========================================================================

    @Test
    void removeMember_asManager_removesMember() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            User manager = user(1L, Role.MANAGER);
            User member = user(2L, Role.DEVELOPER);
            Team team = team(10L, manager);
            team.getMembers().add(member);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(teamRepository.save(team)).thenReturn(team);

            TeamDto result = service.removeMember(10L, 2L);

            assertThat(team.getMembers()).doesNotContain(member);
            assertThat(result.members()).extracting("id").doesNotContain(2L);
        }
    }

    @Test
    void removeMember_notManager_throwsForbidden() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            User manager = user(1L, Role.MANAGER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));

            assertThatThrownBy(() -> service.removeMember(10L, 3L))
                    .isInstanceOf(ForbiddenException.class);

            verify(teamRepository, never()).save(any());
        }
    }

    @Test
    void removeMember_teamNotFound_throwsNoSuchElement() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            when(teamRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeMember(10L, 2L))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // =========================================================================
    // getById
    // =========================================================================

    @Test
    void getById_existingTeam_returnsTeam() {
        User manager = user(1L, Role.MANAGER);
        Team team = team(10L, manager);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));

        Team result = service.getById(10L);

        assertThat(result).isSameAs(team);
    }

    @Test
    void getById_notFound_throwsNoSuchElement() {
        when(teamRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(10L))
                .isInstanceOf(NoSuchElementException.class);
    }

    // =========================================================================
    // renameTeam
    // =========================================================================

    @Test
    void renameTeam_asManager_updatesName() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            User manager = user(1L, Role.MANAGER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(1L)).thenReturn(manager);
            when(teamRepository.save(team)).thenReturn(team);

            TeamDto result = service.renameTeam(10L, "New Name");

            assertThat(result.name()).isEqualTo("New Name");
        }
    }

    @Test
    void renameTeam_asAdminNotManager_updatesName() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            User manager = user(1L, Role.MANAGER);
            User admin = user(2L, Role.ADMIN);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(2L)).thenReturn(admin);
            when(teamRepository.save(team)).thenReturn(team);

            TeamDto result = service.renameTeam(10L, "New Name");

            assertThat(result.name()).isEqualTo("New Name");
        }
    }

    @Test
    void renameTeam_asNonManagerNonAdmin_throwsForbidden() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            User manager = user(1L, Role.MANAGER);
            User other = user(2L, Role.DEVELOPER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(2L)).thenReturn(other);

            assertThatThrownBy(() -> service.renameTeam(10L, "New Name"))
                    .isInstanceOf(ForbiddenException.class);

            verify(teamRepository, never()).save(any());
        }
    }

    @Test
    void renameTeam_teamNotFound_throwsNoSuchElement() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            when(teamRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.renameTeam(10L, "New Name"))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // =========================================================================
    // archiveTeam
    // =========================================================================

    @Test
    void archiveTeam_asManager_setsArchivedAt() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            User manager = user(1L, Role.MANAGER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(1L)).thenReturn(manager);
            when(teamRepository.save(team)).thenReturn(team);

            TeamDto result = service.archiveTeam(10L);

            assertThat(result.archivedAt()).isNotNull();
            assertThat(team.getArchivedAt()).isNotNull();
        }
    }

    @Test
    void archiveTeam_asNonManagerNonAdmin_throwsForbidden() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            User manager = user(1L, Role.MANAGER);
            User other = user(2L, Role.DEVELOPER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(2L)).thenReturn(other);

            assertThatThrownBy(() -> service.archiveTeam(10L))
                    .isInstanceOf(ForbiddenException.class);

            assertThat(team.getArchivedAt()).isNull();
            verify(teamRepository, never()).save(any());
        }
    }

    @Test
    void archiveTeam_teamNotFound_throwsNoSuchElement() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            when(teamRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.archiveTeam(10L))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // =========================================================================
    // updateConfig
    // =========================================================================

    @Test
    void updateConfig_asManager_updatesVisibilityAndSchedule() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            User manager = user(1L, Role.MANAGER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(1L)).thenReturn(manager);
            when(teamRepository.save(team)).thenReturn(team);

            TeamDto result = service.updateConfig(10L, new TeamConfigRequest("WORKSPACE", "MONDAY"));

            assertThat(result.visibility()).isEqualTo("WORKSPACE");
            assertThat(result.aiBriefSchedule()).isEqualTo("MONDAY");
        }
    }

    @Test
    void updateConfig_partialUpdateNullFields_leavesExistingValues() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            User manager = user(1L, Role.MANAGER);
            Team team = team(10L, manager);
            team.setVisibility("PRIVATE");
            team.setAiBriefSchedule("FRIDAY");

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(1L)).thenReturn(manager);
            when(teamRepository.save(team)).thenReturn(team);

            TeamDto result = service.updateConfig(10L, new TeamConfigRequest(null, null));

            assertThat(result.visibility()).isEqualTo("PRIVATE");
            assertThat(result.aiBriefSchedule()).isEqualTo("FRIDAY");
        }
    }

    @Test
    void updateConfig_asNonManagerNonAdmin_throwsForbidden() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            User manager = user(1L, Role.MANAGER);
            User other = user(2L, Role.DEVELOPER);
            Team team = team(10L, manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(2L)).thenReturn(other);

            assertThatThrownBy(() -> service.updateConfig(10L, new TeamConfigRequest("PUBLIC", null)))
                    .isInstanceOf(ForbiddenException.class);

            verify(teamRepository, never()).save(any());
        }
    }

    @Test
    void updateConfig_teamNotFound_throwsNoSuchElement() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(1L)) {
            when(teamRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateConfig(10L, new TeamConfigRequest("PUBLIC", null)))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // =========================================================================
    // duplicateTeam
    // =========================================================================

    @Test
    void duplicateTeam_copiesNameManagerAndMembers() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            User originalManager = user(1L, Role.MANAGER);
            User currentUser = user(2L, Role.MANAGER);
            User member = user(3L, Role.DEVELOPER);

            Team original = team(10L, originalManager);
            Set<User> members = new HashSet<>();
            members.add(member);
            original.setMembers(members);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(original));
            when(userRepository.findById(2L)).thenReturn(Optional.of(currentUser));
            when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
                Team t = inv.getArgument(0);
                t.setId(20L);
                return t;
            });

            TeamDto result = service.duplicateTeam(10L);

            assertThat(result.id()).isEqualTo(20L);
            assertThat(result.name()).isEqualTo("Team 10 (copy)");
            assertThat(result.manager().getId()).isEqualTo(2L);
            assertThat(result.members()).extracting("id").containsExactly(3L);
        }
    }

    @Test
    void duplicateTeam_teamNotFound_throwsNoSuchElement() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            when(teamRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.duplicateTeam(10L))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void duplicateTeam_currentUserNotFound_throwsNoSuchElement() {
        try (MockedStatic<SecurityUtils> su = mockCurrentUser(2L)) {
            User originalManager = user(1L, Role.MANAGER);
            Team original = team(10L, originalManager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(original));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.duplicateTeam(10L))
                    .isInstanceOf(NoSuchElementException.class);

            verify(teamRepository, never()).save(any());
        }
    }
}
