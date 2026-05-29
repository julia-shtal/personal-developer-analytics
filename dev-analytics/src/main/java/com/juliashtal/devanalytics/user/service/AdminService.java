package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.ai.repository.MetricSummaryRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final MetricSummaryRepository metricSummaryRepository;
    private final EntityManager entityManager;

    public long activeUsersLast24h() {
        return userRepository.countActiveUsersLast24h();
    }

    public long databaseSizeBytes() {
        Object result = entityManager
                .createNativeQuery("SELECT pg_database_size(current_database())")
                .getSingleResult();
        return ((Number) result).longValue();
    }

    public long aiCallsToday() {
        return metricSummaryRepository.countAiCallsToday();
    }
}
