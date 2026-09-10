package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.metrics.model.CommitDetailProjection;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * Calculates the after-hours-commit and refactor-ratio metrics (working hours 09–18).
 */
@Component
@RequiredArgsConstructor
public class AfterHoursAndRefactorCalculator implements MetricCalculator {

    private static final int WORK_HOURS_START_HOUR = 9;
    private static final int WORK_HOURS_END_HOUR   = 18;

    private final GitCommitEntityRepository commitRepository;
    private final MetricSnapshotWriter writer;

    @Override
    public Set<MetricType> produces() {
        return Set.of(MetricType.AFTER_HOURS_COMMIT_RATIO, MetricType.REFACTOR_RATIO);
    }

    @Override
    public void calculate(MetricCalcContext ctx) {
        if (ctx.repoIds().isEmpty()) return;
        // No identity: attribute nothing rather than everything, so write no rows.
        if (!ctx.identity().hasCommitIdentity()) return;

        List<CommitDetailProjection> rows = commitRepository
                .findCommitDetailsByRepoIdsAndIdentity(ctx.repoIds(),
                        ctx.identity().githubUserId(),
                        CalcUtils.emailsOrSentinel(ctx.identity().commitEmails()),
                        ctx.from(), ctx.to());
        if (rows.isEmpty()) return;

        ZoneId zone;
        try {
            zone = ZoneId.of(ctx.user().getTimezone() != null ? ctx.user().getTimezone() : "UTC");
        } catch (Exception e) {
            zone = ZoneOffset.UTC;
        }

        long total = rows.size();
        long outOfHours = 0;
        long refactorCount = 0;
        long enrichedTotal = 0;

        for (CommitDetailProjection row : rows) {
            Instant authorDate  = row.getAuthorDate();
            int additions       = row.getAdditions();
            int deletions       = row.getDeletions();
            StatsStatus statsStatus = row.getStatsStatus();

            ZonedDateTime zdt = authorDate.atZone(zone);
            DayOfWeek dow = zdt.getDayOfWeek();
            int hour = zdt.getHour();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean isWorkHours = hour >= WORK_HOURS_START_HOUR && hour < WORK_HOURS_END_HOUR;
            if (isWeekend || !isWorkHours) outOfHours++;

            if (statsStatus == StatsStatus.COMPLETE) {
                enrichedTotal++;
                if (deletions > additions) refactorCount++;
            }
        }

        writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.AFTER_HOURS_COMMIT_RATIO,
                (double) outOfHours / total, null, ctx.fromDate(), ctx.toDate());

        if (enrichedTotal > 0) {
            writer.save(ctx.user(), ctx.team(), ctx.fromDate(), MetricType.REFACTOR_RATIO,
                    (double) refactorCount / enrichedTotal, null, ctx.fromDate(), ctx.toDate());
        }
    }
}
