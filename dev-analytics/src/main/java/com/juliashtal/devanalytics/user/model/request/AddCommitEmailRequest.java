package com.juliashtal.devanalytics.user.model.request;

import lombok.Data;

/**
 * Request to declare an additional address the caller commits with.
 */
@Data
public class AddCommitEmailRequest {
    private String email;
}
