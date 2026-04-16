package com.juliashtal.devanalytics.user.model.request;

import com.juliashtal.devanalytics.user.model.Role;
import lombok.Data;

@Data
public class UpdateRoleRequest {
    private Role role;
}
