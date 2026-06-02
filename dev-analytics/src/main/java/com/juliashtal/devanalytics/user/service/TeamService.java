package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.notification.NotificationDispatchService;
import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.request.TeamConfigRequest;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.TeamDto;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final DataSourceConfigRepository dataSourceConfigRepository;
    private final NotificationDispatchService notificationDispatch;

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
                .filter(t -> t.getArchivedAt() == null)
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
        notificationDispatch.sendNewTeamMemberIfEnabled(member, team.getName());
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

    @Transactional
    public void deleteTeam(Long teamId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        Role role = userRepository.getReferenceById(currentUserId).getRole();
        if (role != Role.ADMIN && !team.getManager().getId().equals(currentUserId)) {
            throw new ForbiddenException("Only the team manager or an admin can delete this team");
        }

        if (!dataSourceConfigRepository.findAllByTeam(team).isEmpty()) {
            throw new ConflictException("Team has attached data sources. Move or delete them before deleting the team.");
        }

        teamRepository.delete(team);
        log.info("Team deleted: teamId={}, deletedBy={}", teamId, currentUserId);
    }

    @Transactional
    public TeamDto archiveTeam(Long teamId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        Role role = userRepository.getReferenceById(currentUserId).getRole();
        if (role != Role.ADMIN && !team.getManager().getId().equals(currentUserId)) {
            throw new ForbiddenException("Only the team manager or an admin can archive this team");
        }

        team.setArchivedAt(Instant.now());
        TeamDto result = TeamDto.from(teamRepository.save(team));
        log.info("Team archived: teamId={}, archivedBy={}", teamId, currentUserId);
        return result;
    }

    @Transactional
    public TeamDto updateConfig(Long teamId, TeamConfigRequest req) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        Role role = userRepository.getReferenceById(currentUserId).getRole();
        if (role != Role.ADMIN && !team.getManager().getId().equals(currentUserId)) {
            throw new ForbiddenException("Only the team manager or an admin can update team config");
        }

        if (req.visibility() != null) team.setVisibility(req.visibility());
        if (req.aiBriefSchedule() != null) team.setAiBriefSchedule(req.aiBriefSchedule());
        TeamDto result = TeamDto.from(teamRepository.save(team));
        log.info("Team config updated: teamId={}, updatedBy={}", teamId, currentUserId);
        return result;
    }

    @Transactional
    public TeamDto duplicateTeam(Long teamId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Team original = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Team copy = new Team();
        copy.setName(original.getName() + " (copy)");
        copy.setManager(currentUser);
        copy.setMembers(new HashSet<>(original.getMembers()));

        TeamDto result = TeamDto.from(teamRepository.save(copy));
        log.info("Team duplicated: originalId={}, newId={}, duplicatedBy={}", teamId, result.getId(), currentUserId);
        return result;
    }
}