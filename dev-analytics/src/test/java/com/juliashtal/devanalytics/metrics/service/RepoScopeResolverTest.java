package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepoScopeResolverTest {

    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock UserRepoRegistrationRepository userRepoRegRepository;
    @Mock TeamRepository teamRepository;

    @InjectMocks RepoScopeResolver resolver;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private Team team(long id) {
        Team t = new Team();
        t.setId(id);
        return t;
    }

    // ── Personal path ──────────────────────────────────────────────────────────

    @Test
    void resolve_personalWithRegistrations_returnsRegistrationsWithoutQueryingTeamRepos() {
        User user = user(1L);
        when(userRepoRegRepository.findRepoIdsByUserId(1L)).thenReturn(List.of(10L, 11L));

        List<Long> result = resolver.resolve(user, null);

        assertThat(result).containsExactly(10L, 11L);
        verifyNoInteractions(teamRepository);
        verifyNoInteractions(gitRepoRepository);
    }

    @Test
    void resolve_personalNoRegistrationsWithTeamMembership_fallsBackToTeamRepos() {
        User user = user(1L);
        Team team = team(5L);
        when(userRepoRegRepository.findRepoIdsByUserId(1L)).thenReturn(List.of());
        when(teamRepository.findByMembersId(1L)).thenReturn(List.of(team));
        when(gitRepoRepository.findIdsByTeamIds(List.of(5L))).thenReturn(List.of(20L, 21L));

        List<Long> result = resolver.resolve(user, null);

        assertThat(result).containsExactly(20L, 21L);
    }

    @Test
    void resolve_personalNoRegistrationsNoTeamMembership_returnsEmpty() {
        User user = user(1L);
        when(userRepoRegRepository.findRepoIdsByUserId(1L)).thenReturn(List.of());
        when(teamRepository.findByMembersId(1L)).thenReturn(List.of());

        List<Long> result = resolver.resolve(user, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(gitRepoRepository);
    }

    // ── Team path ──────────────────────────────────────────────────────────────

    @Test
    void resolve_teamWithTeamRepos_returnsTeamReposWithoutQueryingUserRegistrations() {
        User user = user(1L);
        Team team = team(5L);
        when(gitRepoRepository.findIdsByTeamIds(List.of(5L))).thenReturn(List.of(30L, 31L));

        List<Long> result = resolver.resolve(user, team);

        assertThat(result).containsExactly(30L, 31L);
        verifyNoInteractions(userRepoRegRepository);
    }

    @Test
    void resolve_teamNoTeamRepos_fallsBackToUserRegistrations() {
        User user = user(1L);
        Team team = team(5L);
        when(gitRepoRepository.findIdsByTeamIds(List.of(5L))).thenReturn(List.of());
        when(userRepoRegRepository.findRepoIdsByUserId(1L)).thenReturn(List.of(10L));

        List<Long> result = resolver.resolve(user, team);

        assertThat(result).containsExactly(10L);
    }
}
