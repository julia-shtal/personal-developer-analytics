package com.juliashtal.devanalytics.invite;

import lombok.Data;

@Data
public class CreateInviteRequest {
    private String email;
    private String role;
    private Long teamId;
}
