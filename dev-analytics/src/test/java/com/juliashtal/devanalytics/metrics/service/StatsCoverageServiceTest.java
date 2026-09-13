package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.git.model.StatsSkipReason;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageDto;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageProjection;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageRecordType;
import com.juliashtal.devanalytics.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that coverage shares are computed within a record type, that an empty repo scope short-
 * circuits before any query, and that an explicit repoId bypasses scope resolution.
 */
@ExtendWith(MockitoExtension.class)
class StatsCoverageServiceTest {

    @Mock GitCommitEntityRepository commitRepository;
    @Mock GitHubPullRequestRepository pullRequestRepository;
    @Mock RepoScopeResolver repoScopeResolver;

    @InjectMocks StatsCoverageService service;

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 7);

    private User user;

    @BeforeEach
    void stubUser() {
        user = new User();
        user.setId(1L);
    }

    private static StatsCoverageProjection row(StatsStatus status, StatsSkipReason reason, long count) {
        return new StatsCoverageProjection() {
            public StatsStatus getStatsStatus() { return status; }
            public StatsSkipReason getStatsSkipReason() { return reason; }
            public long getRecordCount() { return count; }
        };
    }

    @Test
    void describeCoverage_mixedStates_sharesSumToOnePerRecordType() {
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(10L));
        when(commitRepository.countByStatsStateInRange(anyList(), any(), any())).thenReturn(List.of(
                row(StatsStatus.COMPLETE, null, 75),
                row(StatsStatus.SKIPPED, StatsSkipReason.DIFF_TOO_LARGE, 15),
                row(StatsStatus.SKIPPED, StatsSkipReason.RECORD_UNAVAILABLE, 10)));
        when(pullRequestRepository.countByStatsStateInRange(anyList(), any(), any())).thenReturn(List.of());

        List<StatsCoverageDto> coverage = service.describeCoverage(user, FROM, TO, null);

        assertThat(coverage).hasSize(3);
        assertThat(coverage.stream()
                .filter(c -> c.recordType() == StatsCoverageRecordType.COMMIT)
                .mapToDouble(StatsCoverageDto::share).sum()).isEqualTo(1.0, within(1e-9));
        assertThat(coverage)
                .extracting(StatsCoverageDto::statsSkipReason, StatsCoverageDto::recordCount)
                .contains(tuple(StatsSkipReason.DIFF_TOO_LARGE, 15L));
    }

    @Test
    void describeCoverage_singleState_shareIsOne() {
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(10L));
        when(commitRepository.countByStatsStateInRange(anyList(), any(), any()))
                .thenReturn(List.of(row(StatsStatus.COMPLETE, null, 4)));
        when(pullRequestRepository.countByStatsStateInRange(anyList(), any(), any())).thenReturn(List.of());

        List<StatsCoverageDto> coverage = service.describeCoverage(user, FROM, TO, null);

        assertThat(coverage).singleElement()
                .satisfies(c -> {
                    assertThat(c.recordCount()).isEqualTo(4L);
                    assertThat(c.share()).isEqualTo(1.0);
                });
    }

    @Test
    void describeCoverage_emptyRepoScope_returnsEmptyWithoutQuerying() {
        // Hibernate renders an empty collection as `in ()`, which PostgreSQL rejects.
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of());

        assertThat(service.describeCoverage(user, FROM, TO, null)).isEmpty();

        verify(commitRepository, never()).countByStatsStateInRange(anyList(), any(), any());
        verify(pullRequestRepository, never()).countByStatsStateInRange(anyList(), any(), any());
    }

    @Test
    void describeCoverage_repoIdGiven_narrowsScopeToThatRepo() {
        when(commitRepository.countByStatsStateInRange(List.of(7L),
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-08T00:00:00Z")))
                .thenReturn(List.of(row(StatsStatus.COMPLETE, null, 1)));
        when(pullRequestRepository.countByStatsStateInRange(anyList(), any(), any())).thenReturn(List.of());

        assertThat(service.describeCoverage(user, FROM, TO, 7L)).hasSize(1);

        verify(repoScopeResolver, never()).resolve(any(), any());
    }

    @Test
    void describeCoverage_commitsAndPullRequests_areReportedSeparately() {
        when(repoScopeResolver.resolve(user, null)).thenReturn(List.of(10L));
        when(commitRepository.countByStatsStateInRange(anyList(), any(), any()))
                .thenReturn(List.of(row(StatsStatus.COMPLETE, null, 2)));
        when(pullRequestRepository.countByStatsStateInRange(anyList(), any(), any()))
                .thenReturn(List.of(row(StatsStatus.SKIPPED, StatsSkipReason.DIFF_TOO_LARGE, 3)));

        List<StatsCoverageDto> coverage = service.describeCoverage(user, FROM, TO, null);

        assertThat(coverage).extracting(StatsCoverageDto::recordType)
                .containsExactlyInAnyOrder(
                        StatsCoverageRecordType.COMMIT, StatsCoverageRecordType.PULL_REQUEST);
        // Shares are per record type, so a lone PR row is 100% of the PR population.
        assertThat(coverage.stream()
                .filter(c -> c.recordType() == StatsCoverageRecordType.PULL_REQUEST)
                .findFirst().orElseThrow().share()).isEqualTo(1.0);
    }
}
