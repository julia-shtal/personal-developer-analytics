package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.metrics.model.DailyCommitsProjection;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Calculates the deep-work streak metric.
 */
@Component
@RequiredArgsConstructor
public class DeepWorkStreakCalculator implements MetricCalculator {

    private final GitCommitEntityRepository commitRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.DEEP_WORK_STREAK_DAYS);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;

        List<DailyCommitsProjection> rows = commitRepository
                .aggregateCommitsDailyByRepoIdsAndAuthorEmail(ctx.repoIds(), ctx.user().getEmail(), ctx.from(), ctx.to());

        TreeSet<LocalDate> daysWithCommits = new TreeSet<>();
        for (DailyCommitsProjection row : rows) {
            if (row.getCommitsCount() > 0) {
                daysWithCommits.add(row.getDay().toLocalDate());
            }
        }
        if (daysWithCommits.isEmpty()) return;

        int maxStreak = 0;
        int streak = 0;
        LocalDate prev = null;
        for (LocalDate day : daysWithCommits) {
            if (prev != null && day.equals(prev.plusDays(1))) {
                streak++;
            } else {
                streak = 1;
            }
            if (streak > maxStreak) maxStreak = streak;
            prev = day;
        }

        writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.DEEP_WORK_STREAK_DAYS,
                maxStreak, null, ctx.fromDate(), ctx.toDate());
    }
}
