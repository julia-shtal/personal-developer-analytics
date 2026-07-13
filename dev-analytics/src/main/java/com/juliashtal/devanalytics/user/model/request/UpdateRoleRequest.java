package com.juliashtal.devanalytics.user.model.request;

import com.juliashtal.devanalytics.user.model.Role;
import lombok.Data;

/**
 * Request to change a user's role.
 */
@Data
public class UpdateRoleRequest {
    private Role role;
}
