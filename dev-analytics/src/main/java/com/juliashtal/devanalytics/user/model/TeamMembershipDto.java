package com.juliashtal.devanalytics.user.model;

import lombok.Data;

/** Lean team view returned to developer-role members: id, name, and member count (no member list). */
@Data
public class TeamMembershipDto {
    private Long id;
    private String name;
    private int memberCount;

    public static TeamMembershipDto from(Team team) {
        TeamMembershipDto dto = new TeamMembershipDto();
        dto.id = team.getId();
        dto.name = team.getName();
        dto.memberCount = team.getMembers() != null ? team.getMembers().size() : 0;
        return dto;
    }
}
