package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.metrics.model.DailyCommitsProjection;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates the focus-ratio metric (days worked vs. distinct tasks).
 */
@Component
@RequiredArgsConstructor
public class FocusRatioCalculator implements MetricCalculator {

    private final GitCommitEntityRepository commitRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.FOCUS_RATIO_DAYS_TASKS);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        // No identity: attribute nothing rather than everything, so write no rows.
        if (!ctx.identity().hasCommitIdentity()) return;

        List<DailyCommitsProjection> rows = commitRepository
                .aggregateCommitsDailyByRepoIdsAndIdentity(ctx.repoIds(),
                        ctx.identity().githubUserId(),
                        CalcUtils.emailsOrSentinel(ctx.identity().commitEmails()),
                        ctx.from(), ctx.to());

        Set<LocalDate> daysWithCommits = new HashSet<>();
        for (DailyCommitsProjection row : rows) {
            if (row.getCommitsCount() > 0) daysWithCommits.add(row.getDay().toLocalDate());
        }

        for (LocalDate day : daysWithCommits) {
            DayOfWeek dow = day.getDayOfWeek();
            boolean inRange  = !day.isBefore(ctx.fromDate()) && !day.isAfter(ctx.toDate());
            boolean isWeekday = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            if (inRange && isWeekday) {
                writer.save(ctx.user(), ctx.team(), day, MetricType.FOCUS_RATIO_DAYS_TASKS, 1.0, null, null, null);
            }
        }
    }
}
