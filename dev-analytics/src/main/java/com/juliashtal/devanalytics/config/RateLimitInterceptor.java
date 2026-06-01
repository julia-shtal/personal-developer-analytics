package com.juliashtal.devanalytics.config;

import com.juliashtal.devanalytics.exception.RateLimitExceededException;
import com.juliashtal.devanalytics.security.SecurityUtils;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-user rate limiter backed by in-memory Bucket4j buckets.
 *
 * <p>Two tiers:
 * <ul>
 *   <li>Default: {@code app.rate-limit.default-rpm} requests/minute per authenticated user.</li>
 *   <li>AI endpoints ({@code /api/ai/}): {@code app.rate-limit.ai-rpm} requests/minute.</li>
 * </ul>
 *
 * <p>Buckets are per-user, per-tier, in a {@link ConcurrentHashMap}.
 * For multi-instance deployments, replace with Redis-backed Bucket4j.
 *
 * <p>Exhausted buckets return 429 with a {@code Retry-After} header via
 * {@link com.juliashtal.devanalytics.exception.GlobalExceptionHandler}.
 */
@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    @Value("${app.rate-limit.default-rpm:120}")
    int defaultRpm;

    @Value("${app.rate-limit.ai-rpm:10}")
    int aiRpm;

    private final ConcurrentHashMap<Long, Bucket> defaultBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Bucket> aiBuckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId;
        try {
            userId = SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return true; // unauthenticated — pass through; Spring Security handles auth
        }

        boolean isAi = request.getRequestURI().startsWith("/api/ai/");
        Bucket bucket = isAi
                ? aiBuckets.computeIfAbsent(userId, id -> newBucket(aiRpm))
                : defaultBuckets.computeIfAbsent(userId, id -> newBucket(defaultRpm));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
            log.warn("Rate limit exceeded: userId={}, path={}, tier={}, retryAfter={}s",
                    userId, request.getRequestURI(), isAi ? "ai" : "default", retryAfter);
            throw new RateLimitExceededException(retryAfter);
        }
        return true;
    }

    private Bucket newBucket(int rpm) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rpm)
                        .refillGreedy(rpm, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
