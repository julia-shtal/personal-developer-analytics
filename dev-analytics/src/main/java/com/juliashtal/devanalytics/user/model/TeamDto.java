package com.juliashtal.devanalytics.user.model;

import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class TeamDto {
    private Long id;
    private String name;
    private UserSummary manager;
    private Set<UserSummary> members;
    private Instant createdAt;

    public static TeamDto from(Team team) {
        TeamDto dto = new TeamDto();
        dto.id = team.getId();
        dto.name = team.getName();
        dto.manager = UserSummary.from(team.getManager());
        dto.members = team.getMembers().stream()
                .map(UserSummary::from)
                .collect(Collectors.toSet());
        dto.createdAt = team.getCreatedAt();
        return dto;
    }
}