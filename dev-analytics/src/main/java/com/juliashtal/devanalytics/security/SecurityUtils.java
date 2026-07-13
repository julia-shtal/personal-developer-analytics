package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.user.model.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Static accessors for the current user's id and role from the security context.
 */
public class SecurityUtils {

    public static Long getCurrentUserId() {
        return getCurrentUserDetails().getId();
    }

    public static Role getCurrentUserRole() {
        return getCurrentUserDetails().getUser().getRole();
    }

    public static CustomUserDetails getCurrentUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new ForbiddenException("No authenticated user");
        }
        return details;
    }
}

