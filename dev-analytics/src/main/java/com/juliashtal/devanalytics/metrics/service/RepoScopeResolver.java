package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves which repository IDs should feed a metric calculation for a given user and scope.
 *
 * Personal scope (team = null):
 *   1. User's explicit repo registrations (primary).
 *   2. Repos belonging to any team the user is a member of (fallback when empty).
 *
 * Team scope (team != null):
 *   1. Repos belonging to the team's own data sources (primary).
 *   2. User's explicit repo registrations (fallback when the team has no team-scoped sources yet).
 *
 * This is distinct from {@link com.juliashtal.devanalytics.git.service.RepoService}, which answers
 * "what repos can this user display?" using the user_accessible_repos view without priority ordering.
 * This resolver answers "what repos should this user's metrics be calculated against?" with explicit
 * priority rules that prevent team repos from polluting personal metrics.
 */
@Component
@RequiredArgsConstructor
public class RepoScopeResolver {

    private final GitRepositoryEntityRepository gitRepoRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;
    private final TeamRepository teamRepository;

    public List<Long> resolve(User user, Team team) {
        if (team != null) {
            return resolveTeam(user, team);
        }
        return resolvePersonal(user);
    }

    private List<Long> resolvePersonal(User user) {
        List<Long> repoIds = userRepoRegRepository.findRepoIdsByUserId(user.getId());
        if (!repoIds.isEmpty()) {
            return repoIds;
        }
        List<Long> memberTeamIds = teamRepository.findByMembersId(user.getId())
                .stream().map(Team::getId).toList();
        if (memberTeamIds.isEmpty()) {
            return List.of();
        }
        return gitRepoRepository.findIdsByTeamIds(memberTeamIds);
    }

    private List<Long> resolveTeam(User user, Team team) {
        List<Long> repoIds = gitRepoRepository.findIdsByTeamIds(List.of(team.getId()));
        if (!repoIds.isEmpty()) {
            return repoIds;
        }
        return userRepoRegRepository.findRepoIdsByUserId(user.getId());
    }
}
