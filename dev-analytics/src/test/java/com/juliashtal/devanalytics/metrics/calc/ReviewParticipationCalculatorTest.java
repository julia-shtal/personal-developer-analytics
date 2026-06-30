package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewParticipationCalculatorTest {

    @Mock GitHubPrReviewRepository prReviewRepository;
    @Mock MetricSnapshotWriter writer;
    @InjectMocks ReviewParticipationCalculator calculator;

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

    @Test
    void calculate_userWithReviews_savesCorrectCount() {
        when(prReviewRepository.countDistinctPrsReviewedByUser(
                eq("alice-gh"), eq(List.of(10L, 11L)),
                eq(Instant.parse("2026-06-01T00:00:00Z")),
                eq(Instant.parse("2026-06-30T00:00:00Z"))))
                .thenReturn(7L);

        calculator.calculate(ctx);

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(writer).save(eq(user), isNull(), eq(LocalDate.of(2026, 6, 1)),
                eq(MetricType.REVIEW_PARTICIPATION_COUNT), valueCaptor.capture(),
                isNull(), eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30)));
        assertThat(valueCaptor.getValue()).isEqualTo(7.0);
    }

    @Test
    void calculate_noReviews_savesZero() {
        when(prReviewRepository.countDistinctPrsReviewedByUser(
                eq("alice-gh"), eq(List.of(10L, 11L)),
                eq(Instant.parse("2026-06-01T00:00:00Z")),
                eq(Instant.parse("2026-06-30T00:00:00Z"))))
                .thenReturn(0L);

        calculator.calculate(ctx);

        ArgumentCaptor<Double> valueCaptor = ArgumentCaptor.forClass(Double.class);
        verify(writer).save(eq(user), isNull(), eq(LocalDate.of(2026, 6, 1)),
                eq(MetricType.REVIEW_PARTICIPATION_COUNT), valueCaptor.capture(),
                isNull(), eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30)));
        assertThat(valueCaptor.getValue()).isEqualTo(0.0);
    }

    @Test
    void calculate_nullGithubLogin_skips() {
        user.setGithubLogin(null);

        calculator.calculate(ctx);

        verifyNoInteractions(prReviewRepository, writer);
    }

    @Test
    void calculate_emptyRepoIds_skips() {
        MetricCalcContext emptyCtx = new MetricCalcContext(
                user, null, List.of(),
                ctx.from(), ctx.to(), ctx.fromDate(), ctx.toDate());

        calculator.calculate(emptyCtx);

        verifyNoInteractions(prReviewRepository, writer);
    }

    @Test
    void produces_returnsReviewParticipationCount() {
        assertThat(calculator.produces()).containsExactly(MetricType.REVIEW_PARTICIPATION_COUNT);
    }
}
