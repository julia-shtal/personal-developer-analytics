package com.juliashtal.devanalytics.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards any non-API, non-asset request to index.html so
 * React Router can handle client-side navigation.
 */
@Controller
public class SpaFallbackController {

    @RequestMapping(value = {"/", "/login", "/register", "/forgot-password", "/reset-password",
            "/welcome", "/dashboard", "/team", "/team-manage", "/datasources",
            "/settings", "/messages", "/goals", "/admin"})
    public String spa(HttpServletRequest request) {
        return "forward:/index.html";
    }
}
