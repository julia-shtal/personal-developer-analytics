package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.metrics.calc.MetricCalcContext;
import com.juliashtal.devanalytics.metrics.calc.MetricCalculator;
import com.juliashtal.devanalytics.metrics.calc.MetricCalculatorRegistry;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.metrics.service.RepoScopeResolver;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock MetricCalculatorRegistry metricCalculatorRegistry;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock RepoScopeResolver repoScopeResolver;

    @InjectMocks MetricsService metricsService;

    @Test
    void calculateDailyMetrics_invokesAllCalculators() {
        User user = new User();
        user.setId(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(10L));

        MetricCalculator calculator = mock(MetricCalculator.class);
        when(metricCalculatorRegistry.all()).thenReturn(List.of(calculator));

        metricsService.calculateDailyMetrics(1L,
                LocalDate.of(2024, 1, 15),
                LocalDate.of(2024, 1, 21));

        verify(metricCalculatorRegistry).all();
        verify(calculator).calculate(any(MetricCalcContext.class));
    }

    @Test
    void calculateDailyMetrics_contextCarriesCorrectDates() {
        User user = new User();
        user.setId(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(10L));

        ArgumentCaptor<MetricCalcContext> ctxCaptor = ArgumentCaptor.forClass(MetricCalcContext.class);
        MetricCalculator calculator = mock(MetricCalculator.class);
        when(metricCalculatorRegistry.all()).thenReturn(List.of(calculator));

        LocalDate from = LocalDate.of(2024, 1, 15);
        LocalDate to   = LocalDate.of(2024, 1, 21);
        metricsService.calculateDailyMetrics(1L, from, to);

        verify(calculator).calculate(ctxCaptor.capture());
        MetricCalcContext ctx = ctxCaptor.getValue();
        assertThat(ctx.fromDate()).isEqualTo(from);
        assertThat(ctx.toDate()).isEqualTo(to);
        assertThat(ctx.user()).isSameAs(user);
    }
}
