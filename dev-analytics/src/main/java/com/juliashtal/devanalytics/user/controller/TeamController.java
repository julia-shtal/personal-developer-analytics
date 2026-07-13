package com.juliashtal.devanalytics.user.controller;

import com.juliashtal.devanalytics.user.model.TeamDto;
import com.juliashtal.devanalytics.user.model.TeamMembershipDto;
import com.juliashtal.devanalytics.user.model.request.TeamConfigRequest;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.model.request.AddTeamMemberRequest;
import com.juliashtal.devanalytics.user.model.request.CreateTeamRequest;
import com.juliashtal.devanalytics.user.model.request.RenameTeamRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for teams. Mounted at /api/teams — team and membership management (MANAGER/ADMIN).
 */
@RestController
@RequestMapping("/api/teams")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "Create a new team with the current user as manager")
    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@RequestBody CreateTeamRequest request) {
        return ResponseEntity.ok(teamService.createTeam(request.getName()));
    }

    @Operation(summary = "List teams managed by the current user")
    @GetMapping
    public ResponseEntity<List<TeamDto>> getMyTeams() {
        return ResponseEntity.ok(teamService.getMyTeams());
    }

    /** Teams the current user is a member of (not manager). Accessible to any authenticated role. */
    @Operation(summary = "List teams the current user belongs to as a member")
    @GetMapping("/me/memberships")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamMembershipDto>> getMyMemberships() {
        return ResponseEntity.ok(teamService.getMyMemberships());
    }

    @Operation(summary = "Add a member to a team")
    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamDto> addMember(
            @PathVariable Long teamId,
            @RequestBody AddTeamMemberRequest request) {
        return ResponseEntity.ok(teamService.addMember(teamId, request.getUserId()));
    }

    @Operation(summary = "Remove a member from a team")
    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<TeamDto> removeMember(
            @PathVariable Long teamId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(teamService.removeMember(teamId, userId));
    }

    @Operation(summary = "Rename a team")
    @PutMapping("/{teamId}")
    public ResponseEntity<TeamDto> renameTeam(
            @PathVariable Long teamId,
            @RequestBody RenameTeamRequest request) {
        return ResponseEntity.ok(teamService.renameTeam(teamId, request.getName()));
    }

    @Operation(summary = "Delete a team. Fails with 409 if any data source is still attached.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive a team. Sets archivedAt; team is excluded from list by default.")
    @PatchMapping("/{id}/archive")
    public ResponseEntity<TeamDto> archiveTeam(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.archiveTeam(id));
    }

    @Operation(summary = "Update team configuration: visibility and AI brief schedule.")
    @PutMapping("/{id}/config")
    public ResponseEntity<TeamDto> updateConfig(
            @PathVariable Long id,
            @RequestBody @Valid TeamConfigRequest request) {
        return ResponseEntity.ok(teamService.updateConfig(id, request));
    }

    @Operation(summary = "Duplicate a team with the same members. Name gets ' (copy)' suffix.")
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<TeamDto> duplicateTeam(@PathVariable Long id) {
        return ResponseEntity.status(201).body(teamService.duplicateTeam(id));
    }
}
