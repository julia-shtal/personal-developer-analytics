package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Spring Security bean used in @PreAuthorize expressions to gate team endpoints.
 * Passes when the caller is an ADMIN, the manager of the specific team, or a member of it.
 */
@Component("teamAccessGuard")
@RequiredArgsConstructor
public class TeamAccessGuard {

    private final TeamRepository teamRepository;

    public boolean canRead(Long teamId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return false;
        }
        Long userId = details.getId();
        Role role = details.getUser().getRole();
        if (role == Role.ADMIN) return true;
        if (teamRepository.existsByIdAndManagerId(teamId, userId)) return true;
        return teamRepository.existsByIdAndMembersId(teamId, userId);
    }
}
