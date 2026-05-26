package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceDeleteTest {

    @Mock TeamRepository teamRepository;
    @Mock UserRepository userRepository;
    @Mock DataSourceConfigRepository dataSourceConfigRepository;
    @InjectMocks TeamService service;

    @Test
    void deleteTeam_asManager_succeeds() {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            User manager = new User();
            manager.setId(1L);
            manager.setRole(Role.MANAGER);
            Team team = new Team();
            team.setId(10L);
            team.setManager(manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(1L)).thenReturn(manager);
            when(dataSourceConfigRepository.findAllByTeam(team)).thenReturn(List.of());

            service.deleteTeam(10L);

            verify(teamRepository).delete(team);
        }
    }

    @Test
    void deleteTeam_asNonManager_throwsForbidden() {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(2L);

            User manager = new User();
            manager.setId(1L);
            User nonManager = new User();
            nonManager.setId(2L);
            nonManager.setRole(Role.DEVELOPER);
            Team team = new Team();
            team.setId(10L);
            team.setManager(manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(2L)).thenReturn(nonManager);

            assertThatThrownBy(() -> service.deleteTeam(10L))
                    .isInstanceOf(ForbiddenException.class);

            verify(teamRepository, never()).delete(any());
        }
    }

    @Test
    void deleteTeam_withAttachedDatasource_throwsConflict() {
        try (MockedStatic<SecurityUtils> su = Mockito.mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            User manager = new User();
            manager.setId(1L);
            manager.setRole(Role.MANAGER);
            Team team = new Team();
            team.setId(10L);
            team.setManager(manager);

            when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
            when(userRepository.getReferenceById(1L)).thenReturn(manager);
            when(dataSourceConfigRepository.findAllByTeam(team)).thenReturn(List.of(new DataSourceConfig()));

            assertThatThrownBy(() -> service.deleteTeam(10L))
                    .isInstanceOf(ConflictException.class);

            verify(teamRepository, never()).delete(any());
        }
    }
}
