package com.juliashtal.devanalytics.user.model;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Team as returned to the client, with manager and members.
 */
public record TeamDto(
        Long id,
        String name,
        UserSummary manager,
        Set<UserSummary> members,
        Instant createdAt,
        Instant archivedAt,
        String visibility,
        String aiBriefSchedule
) {
    public static TeamDto from(Team team) {
        return new TeamDto(
                team.getId(),
                team.getName(),
                UserSummary.from(team.getManager()),
                team.getMembers().stream().map(UserSummary::from).collect(Collectors.toSet()),
                team.getCreatedAt(),
                team.getArchivedAt(),
                team.getVisibility(),
                team.getAiBriefSchedule()
        );
    }
}
