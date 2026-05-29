package com.juliashtal.devanalytics.config;

import com.juliashtal.devanalytics.security.SecurityUtils;
import com.juliashtal.devanalytics.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ActivityInterceptor implements HandlerInterceptor {

    private final UserService userService;

    private static final Duration DEBOUNCE = Duration.ofMinutes(5);
    private final ConcurrentHashMap<Long, Instant> lastTouched = new ConcurrentHashMap<>();

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Instant now = Instant.now();
            Instant last = lastTouched.get(userId);
            if (last == null || Duration.between(last, now).compareTo(DEBOUNCE) > 0) {
                lastTouched.put(userId, now);
                userService.touchLastActive(userId);
            }
        } catch (Exception ignored) {
            // Never let activity tracking interrupt a response
        }
    }
}
