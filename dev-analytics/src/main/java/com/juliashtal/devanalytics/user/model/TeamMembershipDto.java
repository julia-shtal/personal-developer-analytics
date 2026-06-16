package com.juliashtal.devanalytics.user.model;

/** Lean team view returned to developer-role members: id, name, and member count (no member list). */
public record TeamMembershipDto(Long id, String name, int memberCount) {

    public static TeamMembershipDto from(Team team) {
        return new TeamMembershipDto(
                team.getId(),
                team.getName(),
                team.getMembers() != null ? team.getMembers().size() : 0
        );
    }
}
