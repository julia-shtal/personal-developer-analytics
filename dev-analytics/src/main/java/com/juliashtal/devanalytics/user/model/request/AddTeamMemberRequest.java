package com.juliashtal.devanalytics.user.model.request;

import lombok.Data;

/**
 * Request to add a user to a team.
 */
@Data
public class AddTeamMemberRequest {
    private Long userId;
}
