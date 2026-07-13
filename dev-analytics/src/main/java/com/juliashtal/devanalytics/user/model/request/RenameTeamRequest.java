package com.juliashtal.devanalytics.user.model.request;

import lombok.Data;

/**
 * Request to rename a team.
 */
@Data
public class RenameTeamRequest {
    private String name;
}
