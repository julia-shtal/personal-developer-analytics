package com.juliashtal.devanalytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.juliashtal.devanalytics.ai.model.MetricSummaryEntity;
import com.juliashtal.devanalytics.ai.model.MetricsSummaryDto;
import com.juliashtal.devanalytics.ai.repository.MetricSummaryRepository;
import com.juliashtal.devanalytics.ai.service.MetricSummaryPersistenceService;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricSummaryPersistenceServiceTest {

    @Mock MetricSummaryRepository summaryRepository;

    MetricSummaryPersistenceService service;

    private final LocalDate from = LocalDate.of(2024, 1, 1);
    private final LocalDate to   = LocalDate.of(2024, 1, 31);

    @BeforeEach
    void setUp() {
        service = new MetricSummaryPersistenceService(summaryRepository,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void savePersonal_insertsNewRow_whenNoExistingEntry() {
        User user = new User();
        user.setId(1L);

        when(summaryRepository.findByIdentity(eq(1L), isNull(), eq(from), eq(to), eq("PERSONAL"), isNull()))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MetricsSummaryDto dto = dto("PERSONAL", null);
        service.savePersonal(user, dto);

        ArgumentCaptor<MetricSummaryEntity> captor = ArgumentCaptor.forClass(MetricSummaryEntity.class);
        verify(summaryRepository).save(captor.capture());
        MetricSummaryEntity saved = captor.getValue();

        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getTeam()).isNull();
        assertThat(saved.getScope()).isEqualTo("PERSONAL");
        assertThat(saved.getHeadline()).isEqualTo("Test headline");
        assertThat(saved.getGeneratedAt()).isNotNull();
    }

    @Test
    void savePersonal_updatesExistingRow_whenEntryAlreadyExists() {
        User user = new User();
        user.setId(1L);

        MetricSummaryEntity existing = new MetricSummaryEntity();
        existing.setId(42L);

        when(summaryRepository.findByIdentity(eq(1L), isNull(), eq(from), eq(to), eq("PERSONAL"), isNull()))
                .thenReturn(Optional.of(existing));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.savePersonal(user, dto("PERSONAL", null));

        ArgumentCaptor<MetricSummaryEntity> captor = ArgumentCaptor.forClass(MetricSummaryEntity.class);
        verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(42L);
        assertThat(captor.getValue().getHeadline()).isEqualTo("Test headline");
    }

    @Test
    void saveTeam_setsNullUser_andTeamReference() {
        Team team = new Team();
        team.setId(7L);

        when(summaryRepository.findByIdentity(isNull(), eq(7L), eq(from), eq(to), eq("TEAM"), eq("Alpha")))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MetricsSummaryDto dto = dto("TEAM", "Alpha");
        service.saveTeam(team, dto);

        ArgumentCaptor<MetricSummaryEntity> captor = ArgumentCaptor.forClass(MetricSummaryEntity.class);
        verify(summaryRepository).save(captor.capture());
        MetricSummaryEntity saved = captor.getValue();

        assertThat(saved.getUser()).isNull();
        assertThat(saved.getTeam()).isEqualTo(team);
        assertThat(saved.getScope()).isEqualTo("TEAM");
        assertThat(saved.getContextRepoName()).isEqualTo("Alpha");
    }

    @Test
    void savePersonal_skipsEmptyParseResult() {
        User user = new User();
        user.setId(1L);

        MetricsSummaryDto emptyDto = MetricsSummaryDto.builder()
                .from(from).to(to).scope("PERSONAL")
                .headline("").insights(List.of()).recommendations(List.of())
                .rawModelOutput("bad json").modelName("llama3.2")
                .build();

        service.savePersonal(user, emptyDto);

        verify(summaryRepository, never()).save(any());
    }

    private MetricsSummaryDto dto(String scope, String contextName) {
        return MetricsSummaryDto.builder()
                .from(from).to(to)
                .scope(scope)
                .contextRepoName(contextName)
                .headline("Test headline")
                .overview("Overview text.")
                .insights(List.of(MetricsSummaryDto.InsightDto.builder()
                        .kind("positive").text("PR lead time improved.").metric("PR Lead Time").build()))
                .recommendations(List.of("action 1"))
                .rawModelOutput("{}")
                .modelName("llama3.2")
                .build();
    }
}
