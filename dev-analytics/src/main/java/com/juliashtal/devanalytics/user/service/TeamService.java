package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.TeamDto;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional
    public TeamDto createTeam(String name) {
        Long managerId = SecurityUtils.getCurrentUserId();
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Team team = new Team();
        team.setName(name);
        team.setManager(manager);
        TeamDto saved = TeamDto.from(teamRepository.save(team));
        log.info("Team created: name='{}', managerId={}", name, managerId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TeamDto> getMyTeams() {
        Long managerId = SecurityUtils.getCurrentUserId();
        return teamRepository.findByManagerId(managerId).stream()
                .map(TeamDto::from)
                .toList();
    }

    @Transactional
    public TeamDto addMember(Long teamId, Long userId) {
        Long managerId = SecurityUtils.getCurrentUserId();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found"));

        if (!team.getManager().getId().equals(managerId)) {
            throw new ForbiddenException("You are not the manager of this team");
        }

        User member = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        team.getMembers().add(member);
        TeamDto result = TeamDto.from(teamRepository.save(team));
        log.info("Member userId={} added to teamId={}", userId, teamId);
        return result;
    }

    @Transactional
    public TeamDto removeMember(Long teamId, Long userId) {
        Long managerId = SecurityUtils.getCurrentUserId();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found"));

        if (!team.getManager().getId().equals(managerId)) {
            throw new ForbiddenException("You are not the manager of this team");
        }

        team.getMembers().removeIf(m -> m.getId().equals(userId));
        TeamDto result = TeamDto.from(teamRepository.save(team));
        log.info("Member userId={} removed from teamId={}", userId, teamId);
        return result;
    }

    @Transactional(readOnly = true)
    public Team getById(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));
    }

    @Transactional
    public TeamDto renameTeam(Long teamId, String name) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found"));

        Role role = userRepository.getReferenceById(currentUserId).getRole();
        if (role != Role.ADMIN && !team.getManager().getId().equals(currentUserId)) {
            throw new ForbiddenException("Only the team manager or an admin can rename this team");
        }
        team.setName(name);
        TeamDto result = TeamDto.from(teamRepository.save(team));
        log.info("Team renamed: teamId={}, newName='{}'", teamId, name);
        return result;
    }
}