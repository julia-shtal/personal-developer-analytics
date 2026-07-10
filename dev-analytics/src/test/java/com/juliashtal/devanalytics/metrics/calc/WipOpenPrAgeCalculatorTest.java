package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WipOpenPrAgeCalculatorTest {

    @Mock GitHubPullRequestRepository pullRequestRepository;
    @Mock GitRepositoryEntityRepository gitRepoRepository;
    @Mock MetricSnapshotWriter writer;
    @InjectMocks WipOpenPrAgeCalculator calculator;

    private User user;
    private MetricCalcContext ctx;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setGithubLogin("alice-gh");

        ctx = new MetricCalcContext(
                user, null, List.of(10L, 11L),
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T00:00:00Z"),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30));
    }

    private GitHubPullRequestEntity openPr(long repoId, long ageHours) {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setId(repoId);
        GitHubPullRequestEntity pr = new GitHubPullRequestEntity();
        pr.setRepository(repo);
        pr.setAuthorLogin("alice-gh");
        pr.setCreatedAt(Instant.now().minus(ageHours, ChronoUnit.HOURS));
        return pr;
    }

    @Test
    void calculate_openPrs_savesPerRepoMedianAge() {
        when(pullRequestRepository.findOpenPrsByRepoIdsAndAuthorLogin(List.of(10L, 11L), "alice-gh"))
                .thenReturn(List.of(openPr(10L, 24), openPr(10L, 72), openPr(11L, 5)));
        GitRepositoryEntity repo10 = new GitRepositoryEntity(); repo10.setId(10L);
        GitRepositoryEntity repo11 = new GitRepositoryEntity(); repo11.setId(11L);
        when(gitRepoRepository.getReferenceById(10L)).thenReturn(repo10);
        when(gitRepoRepository.getReferenceById(11L)).thenReturn(repo11);

        calculator.calculate(ctx);

        ArgumentCaptor<Double> value = ArgumentCaptor.forClass(Double.class);
        // repo 10: median(24,72)=48 ; repo 11: single=5
        verify(writer).save(eq(user), isNull(), eq(LocalDate.of(2026, 6, 1)),
                eq(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN), value.capture(),
                eq(repo10), eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30)));
        assertThat(value.getValue()).isCloseTo(48.0, org.assertj.core.data.Offset.offset(1.0));

        ArgumentCaptor<Double> value11 = ArgumentCaptor.forClass(Double.class);
        verify(writer).save(eq(user), isNull(), eq(LocalDate.of(2026, 6, 1)),
                eq(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN), value11.capture(),
                eq(repo11), eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30)));
        assertThat(value11.getValue()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void calculate_noOpenPrs_savesNothing() {
        when(pullRequestRepository.findOpenPrsByRepoIdsAndAuthorLogin(List.of(10L, 11L), "alice-gh"))
                .thenReturn(List.of());

        calculator.calculate(ctx);

        verifyNoInteractions(writer);
    }

    @Test
    void calculate_nullGithubLogin_skips() {
        user.setGithubLogin(null);

        calculator.calculate(ctx);

        verifyNoInteractions(pullRequestRepository, writer);
    }

    @Test
    void calculate_emptyRepoIds_skips() {
        MetricCalcContext emptyCtx = new MetricCalcContext(
                user, null, List.of(),
                ctx.from(), ctx.to(), ctx.fromDate(), ctx.toDate());

        calculator.calculate(emptyCtx);

        verifyNoInteractions(pullRequestRepository, writer);
    }

    @Test
    void produces_returnsWipOpenPrAge() {
        assertThat(calculator.produces()).containsExactly(MetricType.WIP_OPEN_PR_AGE_HOURS_MEDIAN);
    }
}
