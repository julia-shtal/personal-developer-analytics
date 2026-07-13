package com.juliashtal.devanalytics.invite;

import lombok.Data;

/**
 * Request to create a team/role invite.
 */
@Data
public class CreateInviteRequest {
    private String email;
    private String role;
    private Long teamId;
}
