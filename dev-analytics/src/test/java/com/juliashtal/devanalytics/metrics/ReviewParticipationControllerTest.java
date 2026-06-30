package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.service.MetricSnapshotService;
import com.juliashtal.devanalytics.metrics.service.MetricsAnomalyService;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MetricsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReviewParticipationControllerTest {

    @Autowired MockMvc mvc;

    @MockBean MetricSnapshotService snapshotService;
    @MockBean MetricsService metricsService;
    @MockBean MetricsAnomalyService anomalyService;
    @MockBean RepoService repoService;
    @MockBean UserService userService;
    @MockBean CheckHelper checkHelper;
    @MockBean MetricSnapshotRepository snapshotRepository;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("alice");
        when(checkHelper.currentUser()).thenReturn(currentUser);
    }

    private MetricSnapshot aggregateSnapshot(MetricType type, double value, LocalDate from, LocalDate to) {
        MetricSnapshot s = new MetricSnapshot();
        s.setMetricType(type);
        s.setDate(to);
        s.setValue(value);
        s.setPeriodFrom(from);
        s.setPeriodTo(to);
        return s;
    }

    @Test
    @WithMockUser
    void getReviewParticipation_authenticated_returns200() throws Exception {
        when(snapshotService.getMetricSnapshotsByUserAndMetricTypeAndDateFromAndTo(
                any(), eq(MetricType.REVIEW_PARTICIPATION_COUNT), eq(FROM), eq(TO)))
                .thenReturn(List.of(aggregateSnapshot(MetricType.REVIEW_PARTICIPATION_COUNT, 5.0, FROM, TO)));

        mvc.perform(get("/api/metrics/review-participation")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(5.0))
                .andExpect(jsonPath("$.metricType").value("REVIEW_PARTICIPATION_COUNT"));
    }

}
