package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.ai.repository.MetricSummaryRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.AdminService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock UserRepository userRepository;
    @Mock MetricSummaryRepository metricSummaryRepository;
    @Mock EntityManager entityManager;
    @InjectMocks AdminService service;

    @Test
    void activeUsersLast24h_countsCorrectly() {
        when(userRepository.countActiveUsersLast24h()).thenReturn(3L);
        assertThat(service.activeUsersLast24h()).isEqualTo(3L);
    }

    @Test
    void databaseSizeBytes_returnsPositive() {
        Query q = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(12_345_678L);
        assertThat(service.databaseSizeBytes()).isEqualTo(12_345_678L);
    }

    @Test
    void aiCallsToday_countsToday() {
        when(metricSummaryRepository.countAiCallsToday()).thenReturn(5L);
        assertThat(service.aiCallsToday()).isEqualTo(5L);
    }
}
