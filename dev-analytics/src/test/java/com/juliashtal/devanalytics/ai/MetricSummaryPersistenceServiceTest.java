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
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
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
    ObjectMapper objectMapper;

    private final LocalDate from = LocalDate.of(2024, 1, 1);
    private final LocalDate to   = LocalDate.of(2024, 1, 31);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new MetricSummaryPersistenceService(summaryRepository, objectMapper);
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

    @Test
    void findLatestPersonal_existingEntry_returnsMappedDto() throws Exception {
        User user = new User();
        user.setId(1L);

        MetricSummaryEntity entity = new MetricSummaryEntity();
        entity.setId(42L);
        entity.setUser(user);
        entity.setPeriodFrom(from);
        entity.setPeriodTo(to);
        entity.setScope("PERSONAL");
        entity.setHeadline("Great week");
        entity.setOverview("Solid progress.");
        entity.setInsights(objectMapper.writeValueAsString(List.of(
                MetricsSummaryDto.InsightDto.builder()
                        .kind("positive").metric("PR Lead Time").text("improved").build())));
        entity.setRecommendations(objectMapper.writeValueAsString(List.of("Keep reviewing PRs promptly")));
        entity.setModelName("llama3.2");
        entity.setGeneratedAt(Instant.now());

        when(summaryRepository.findTopByUser_IdOrderByGeneratedAtDesc(1L)).thenReturn(Optional.of(entity));

        Optional<MetricsSummaryDto> result = service.findLatestPersonal(user);

        assertThat(result).isPresent();
        MetricsSummaryDto dto = result.get();
        assertThat(dto.getHeadline()).isEqualTo("Great week");
        assertThat(dto.getInsights()).hasSize(1);
        assertThat(dto.getInsights().get(0).getMetric()).isEqualTo("PR Lead Time");
        assertThat(dto.getRecommendations()).containsExactly("Keep reviewing PRs promptly");
    }

    @Test
    void findLatestPersonal_noEntry_returnsEmpty() {
        User user = new User();
        user.setId(2L);
        when(summaryRepository.findTopByUser_IdOrderByGeneratedAtDesc(2L)).thenReturn(Optional.empty());

        assertThat(service.findLatestPersonal(user)).isEmpty();
    }

    @Test
    void findLatestTeam_existingEntry_returnsMappedDto() throws Exception {
        Team team = new Team();
        team.setId(7L);

        MetricSummaryEntity entity = new MetricSummaryEntity();
        entity.setId(43L);
        entity.setTeam(team);
        entity.setPeriodFrom(from);
        entity.setPeriodTo(to);
        entity.setScope("TEAM");
        entity.setContextRepoName("Backend Guild");
        entity.setHeadline("Team headline");
        entity.setOverview("Team overview.");
        entity.setInsights(objectMapper.writeValueAsString(List.of()));
        entity.setRecommendations(objectMapper.writeValueAsString(List.of()));
        entity.setModelName("llama3.2");
        entity.setGeneratedAt(Instant.now());

        when(summaryRepository.findTopByTeam_IdOrderByGeneratedAtDesc(7L)).thenReturn(Optional.of(entity));

        Optional<MetricsSummaryDto> result = service.findLatestTeam(team);

        assertThat(result).isPresent();
        assertThat(result.get().getContextRepoName()).isEqualTo("Backend Guild");
        assertThat(result.get().getScope()).isEqualTo("TEAM");
    }

    @Test
    void findHistoryPersonal_returnsMappedList() throws Exception {
        User user = new User();
        user.setId(1L);

        MetricSummaryEntity e1 = new MetricSummaryEntity();
        e1.setId(1L);
        e1.setUser(user);
        e1.setPeriodFrom(from);
        e1.setPeriodTo(to);
        e1.setScope("PERSONAL");
        e1.setHeadline("Week 1");
        e1.setInsights(objectMapper.writeValueAsString(List.of()));
        e1.setRecommendations(objectMapper.writeValueAsString(List.of()));
        e1.setGeneratedAt(Instant.now());

        MetricSummaryEntity e2 = new MetricSummaryEntity();
        e2.setId(2L);
        e2.setUser(user);
        e2.setPeriodFrom(from);
        e2.setPeriodTo(to);
        e2.setScope("PERSONAL");
        e2.setHeadline("Week 2");
        e2.setInsights(objectMapper.writeValueAsString(List.of()));
        e2.setRecommendations(objectMapper.writeValueAsString(List.of()));
        e2.setGeneratedAt(Instant.now());

        when(summaryRepository.findByUser_IdOrderByGeneratedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of(e2, e1));

        List<MetricsSummaryDto> result = service.findHistoryPersonal(user, 5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getHeadline()).isEqualTo("Week 2");
        assertThat(result.get(1).getHeadline()).isEqualTo("Week 1");
    }

    @Test
    void findHistoryTeam_returnsMappedList() throws Exception {
        Team team = new Team();
        team.setId(7L);

        MetricSummaryEntity e1 = new MetricSummaryEntity();
        e1.setId(3L);
        e1.setTeam(team);
        e1.setPeriodFrom(from);
        e1.setPeriodTo(to);
        e1.setScope("TEAM");
        e1.setHeadline("Team week 1");
        e1.setInsights(objectMapper.writeValueAsString(List.of()));
        e1.setRecommendations(objectMapper.writeValueAsString(List.of()));
        e1.setGeneratedAt(Instant.now());

        when(summaryRepository.findByTeam_IdOrderByGeneratedAtDesc(eq(7L), any(PageRequest.class)))
                .thenReturn(List.of(e1));

        List<MetricsSummaryDto> result = service.findHistoryTeam(team, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeadline()).isEqualTo("Team week 1");
    }

    @Test
    void findLatestPersonal_entityWithNullInsightsAndRecommendations_returnsEmptyLists() {
        User user = new User();
        user.setId(3L);

        MetricSummaryEntity entity = new MetricSummaryEntity();
        entity.setId(44L);
        entity.setUser(user);
        entity.setPeriodFrom(from);
        entity.setPeriodTo(to);
        entity.setScope("PERSONAL");
        entity.setHeadline("No insights yet");
        entity.setInsights(null);
        entity.setRecommendations(null);
        entity.setGeneratedAt(Instant.now());

        when(summaryRepository.findTopByUser_IdOrderByGeneratedAtDesc(3L)).thenReturn(Optional.of(entity));

        MetricsSummaryDto dto = service.findLatestPersonal(user).orElseThrow();

        assertThat(dto.getInsights()).isEmpty();
        assertThat(dto.getRecommendations()).isEmpty();
    }

    @Test
    void findLatestPersonal_entityWithMalformedJson_returnsEmptyLists() {
        User user = new User();
        user.setId(4L);

        MetricSummaryEntity entity = new MetricSummaryEntity();
        entity.setId(45L);
        entity.setUser(user);
        entity.setPeriodFrom(from);
        entity.setPeriodTo(to);
        entity.setScope("PERSONAL");
        entity.setHeadline("Broken JSON");
        entity.setInsights("not json");
        entity.setRecommendations("also not json");
        entity.setGeneratedAt(Instant.now());

        when(summaryRepository.findTopByUser_IdOrderByGeneratedAtDesc(4L)).thenReturn(Optional.of(entity));

        MetricsSummaryDto dto = service.findLatestPersonal(user).orElseThrow();

        assertThat(dto.getInsights()).isEmpty();
        assertThat(dto.getRecommendations()).isEmpty();
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
