package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional
    public Team createTeam(String name) {
        Long managerId = SecurityUtils.getCurrentUserId();
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        Team team = new Team();
        team.setName(name);
        team.setManager(manager);
        return teamRepository.save(team);
    }

    public List<Team> getMyTeams() {
        Long managerId = SecurityUtils.getCurrentUserId();
        return teamRepository.findByManagerId(managerId);
    }

    @Transactional
    public Team addMember(Long teamId, Long userId) {
        Long managerId = SecurityUtils.getCurrentUserId();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found"));

        if (!team.getManager().getId().equals(managerId)) {
            throw new IllegalArgumentException("You are not the manager of this team");
        }

        User member = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        team.getMembers().add(member);
        return teamRepository.save(team);
    }

    @Transactional
    public Team removeMember(Long teamId, Long userId) {
        Long managerId = SecurityUtils.getCurrentUserId();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found"));

        if (!team.getManager().getId().equals(managerId)) {
            throw new IllegalArgumentException("You are not the manager of this team");
        }

        team.getMembers().removeIf(m -> m.getId().equals(userId));
        return teamRepository.save(team);
    }
}
