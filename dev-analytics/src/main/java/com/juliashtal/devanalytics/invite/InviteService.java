package com.juliashtal.devanalytics.invite;

import com.juliashtal.devanalytics.exception.BadRequestException;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Creates and validates time-limited team invitations (7-day TTL).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InviteService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final InviteTokenRepository inviteTokenRepository;
    private final TeamRepository teamRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public InviteTokenDto createInvite(User admin, String email, Role role, Long teamId) {
        Team team = teamId != null
                ? teamRepository.findById(teamId).orElseThrow(() -> new BadRequestException("Team not found"))
                : null;

        InviteTokenEntity invite = new InviteTokenEntity();
        invite.setToken(UUID.randomUUID().toString());
        invite.setEmail(email);
        invite.setRole(role);
        invite.setTeam(team);
        invite.setCreatedBy(admin);
        invite.setExpiresAt(Instant.now().plus(INVITE_TTL));

        InviteTokenEntity saved = inviteTokenRepository.save(invite);
        log.info("Invite created: email={}, role={}, teamId={}", email, role, teamId);

        String inviteUrl = frontendUrl + "/register?invite=" + saved.getToken();
        return new InviteTokenDto(saved.getToken(), saved.getEmail(), saved.getRole().name(),
                saved.getExpiresAt(), inviteUrl);
    }

    public InviteInfoDto validateInvite(String token) {
        InviteTokenEntity invite = loadAndCheck(token);
        String teamName = invite.getTeam() != null ? invite.getTeam().getName() : null;
        return new InviteInfoDto(invite.getEmail(), invite.getRole().name(), teamName);
    }

    @Transactional
    public void redeemInvite(String token, User newUser) {
        InviteTokenEntity invite = loadAndCheck(token);
        invite.setUsedAt(Instant.now());
        inviteTokenRepository.save(invite);

        if (invite.getTeam() != null) {
            Team team = teamRepository.findById(invite.getTeam().getId())
                    .orElseThrow(() -> new BadRequestException("Team no longer exists"));
            team.getMembers().add(newUser);
            teamRepository.save(team);
            log.info("User {} added to team {} via invite", newUser.getId(), team.getId());
        }
    }

    private InviteTokenEntity loadAndCheck(String token) {
        InviteTokenEntity invite = inviteTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid invite token"));
        if (invite.getUsedAt() != null) {
            throw new BadRequestException("This invite has already been used");
        }
        if (Instant.now().isAfter(invite.getExpiresAt())) {
            throw new BadRequestException("This invite has expired");
        }
        return invite;
    }
}
