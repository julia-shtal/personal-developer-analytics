package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.controller.AiSummaryController;
import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.security.CheckHelper;
import com.juliashtal.devanalytics.security.service.CustomUserDetailsService;
import com.juliashtal.devanalytics.security.service.JwtService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiSummaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiSummaryControllerTest {

    @Autowired MockMvc mvc;
    @MockBean MetricsAiService metricsAiService;
    @MockBean CheckHelper checkHelper;
    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService customUserDetailsService;

    private static final Long TEAM_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    private MetricsSummaryDto stubbedSummary() {
        return MetricsSummaryDto.builder()
                .from(LocalDate.of(2024, 1, 1))
                .to(LocalDate.of(2024, 1, 31))
                .scope("PERSONAL")
                .headline("Consistent delivery week")
                .overview("Member maintained a steady commit cadence.")
                .insights(List.of())
                .recommendations(List.of())
                .rawModelOutput("{}")
                .modelName("llama3.2")
                .build();
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void getMemberSummary_asManager_succeeds() throws Exception {
        User manager = new User();
        manager.setId(10L);
        when(checkHelper.currentUser()).thenReturn(manager);
        when(metricsAiService.generateMemberSummary(any(), eq(TEAM_ID), eq(MEMBER_ID), any(), any()))
                .thenReturn(stubbedSummary());

        mvc.perform(get("/api/ai/summary/teams/{teamId}/member/{memberId}", TEAM_ID, MEMBER_ID)
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Consistent delivery week"))
                .andExpect(jsonPath("$.scope").value("PERSONAL"));
    }

    @Test
    @WithMockUser
    void getMemberSummary_asNonManager_returns403() throws Exception {
        User nonManager = new User();
        nonManager.setId(99L);
        when(checkHelper.currentUser()).thenReturn(nonManager);
        when(metricsAiService.generateMemberSummary(any(), eq(TEAM_ID), eq(MEMBER_ID), any(), any()))
                .thenThrow(new ForbiddenException("Only the team manager or an admin can generate member AI summaries"));

        mvc.perform(get("/api/ai/summary/teams/{teamId}/member/{memberId}", TEAM_ID, MEMBER_ID)
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isForbidden());
    }
}
