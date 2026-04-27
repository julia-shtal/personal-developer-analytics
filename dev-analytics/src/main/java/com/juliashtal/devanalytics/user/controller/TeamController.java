package com.juliashtal.devanalytics.user.controller;

import com.juliashtal.devanalytics.user.model.TeamDto;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.model.request.AddTeamMemberRequest;
import com.juliashtal.devanalytics.user.model.request.CreateTeamRequest;
import com.juliashtal.devanalytics.user.model.request.RenameTeamRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@RequestBody CreateTeamRequest request) {
        return ResponseEntity.ok(teamService.createTeam(request.getName()));
    }

    @GetMapping
    public ResponseEntity<List<TeamDto>> getMyTeams() {
        return ResponseEntity.ok(teamService.getMyTeams());
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamDto> addMember(
            @PathVariable Long teamId,
            @RequestBody AddTeamMemberRequest request) {
        return ResponseEntity.ok(teamService.addMember(teamId, request.getUserId()));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<TeamDto> removeMember(
            @PathVariable Long teamId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(teamService.removeMember(teamId, userId));
    }

    @PutMapping("/{teamId}")
    public ResponseEntity<TeamDto> renameTeam(
            @PathVariable Long teamId,
            @RequestBody RenameTeamRequest request) {
        return ResponseEntity.ok(teamService.renameTeam(teamId, request.getName()));
    }
}
