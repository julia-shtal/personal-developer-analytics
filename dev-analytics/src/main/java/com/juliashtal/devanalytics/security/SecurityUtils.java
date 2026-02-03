package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new ForbiddenException("No authenticated user");
        }
        return details.getId();
    }
}

