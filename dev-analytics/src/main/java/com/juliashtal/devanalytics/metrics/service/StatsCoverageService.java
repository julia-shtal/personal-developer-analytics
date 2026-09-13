package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageDto;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageProjection;
import com.juliashtal.devanalytics.metrics.model.StatsCoverageRecordType;
import com.juliashtal.devanalytics.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * How much of a window's ingested history actually carries line-count statistics.
 *
 * <p>The line-count metrics are computed only over records whose stats were retrieved, so their
 * excluded set has to be reportable rather than assumed. Window bounds and repo scope mirror
 * {@link MetricsService} exactly, so a coverage figure describes the same population the metrics
 * were calculated over.</p>
 */
@Service
@RequiredArgsConstructor
public class StatsCoverageService {

    private final GitCommitEntityRepository commitRepository;
    private final GitHubPullRequestRepository pullRequestRepository;
    private final RepoScopeResolver repoScopeResolver;

    /**
     * Coverage rows for the user's personal scope, or for one repository when {@code repoId} is
     * given. Callers are responsible for the entitlement check on {@code repoId}.
     */
    public List<StatsCoverageDto> describeCoverage(User user, LocalDate from, LocalDate to, Long repoId) {
        List<Long> repoIds = repoId != null
                ? List.of(repoId)
                : repoScopeResolver.resolve(user, null);

        // Hibernate renders an empty collection as `in ()`, which PostgreSQL rejects.
        if (repoIds.isEmpty()) {
            return List.of();
        }

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<StatsCoverageDto> coverage = new ArrayList<>();
        coverage.addAll(toDtos(StatsCoverageRecordType.COMMIT,
                commitRepository.countByStatsStateInRange(repoIds, fromInstant, toInstant)));
        coverage.addAll(toDtos(StatsCoverageRecordType.PULL_REQUEST,
                pullRequestRepository.countByStatsStateInRange(repoIds, fromInstant, toInstant)));
        return coverage;
    }

    private static List<StatsCoverageDto> toDtos(StatsCoverageRecordType type,
                                                 List<StatsCoverageProjection> rows) {
        long total = rows.stream().mapToLong(StatsCoverageProjection::getRecordCount).sum();
        if (total == 0) {
            return List.of();
        }
        return rows.stream()
                .map(r -> new StatsCoverageDto(
                        type,
                        r.getStatsStatus(),
                        r.getStatsSkipReason(),
                        r.getRecordCount(),
                        (double) r.getRecordCount() / total))
                .toList();
    }
}
