package com.juliashtal.devanalytics.user.model.request;

import lombok.Data;

/**
 * Request to change the current user's password.
 */
@Data
public class ChangePasswordRequest {
    private String oldPassword;
    private String newPassword;
}
