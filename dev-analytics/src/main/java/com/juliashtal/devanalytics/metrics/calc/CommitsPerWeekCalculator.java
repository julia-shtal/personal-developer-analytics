package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.metrics.model.DailyCommitsProjection;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Calculates the average number of commits per ISO calendar week from commit history.
 */
@Component
@RequiredArgsConstructor
public class CommitsPerWeekCalculator implements MetricCalculator {

    private final GitCommitEntityRepository commitRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.COMMITS_PER_WEEK_AVG);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        // Neither a declared address nor a GitHub account: attribute nothing rather
        // than everything. A calculator without an identity must write no rows.
        if (!ctx.identity().hasCommitIdentity()) return;

        List<DailyCommitsProjection> rows = commitRepository
                .aggregateCommitsDailyByRepoIdsAndIdentity(ctx.repoIds(),
                        ctx.identity().githubUserId(),
                        CalcUtils.emailsOrSentinel(ctx.identity().commitEmails()),
                        ctx.from(), ctx.to());
        if (rows.isEmpty()) return;

        Map<String, Long> byWeek = new TreeMap<>();
        for (DailyCommitsProjection row : rows) {
            LocalDate day = row.getDay().toLocalDate();
            long count = row.getCommitsCount();
            if (count == 0) continue;
            int weekYear = day.get(WeekFields.ISO.weekBasedYear());
            int weekNum  = day.get(WeekFields.ISO.weekOfWeekBasedYear());
            String key = weekYear + "-W" + String.format("%02d", weekNum);
            byWeek.merge(key, count, Long::sum);
        }
        if (byWeek.isEmpty()) return;

        double avgPerWeek = byWeek.values().stream().mapToLong(Long::longValue).average().orElse(0.0);
        writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.COMMITS_PER_WEEK_AVG,
                avgPerWeek, null, ctx.fromDate(), ctx.toDate());
    }
}
