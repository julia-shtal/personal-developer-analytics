# Merge Frequency

**Framework:** DORA
**Category:** Performance (Deployment Frequency proxy)
**Unit:** commits per ISO calendar week (average)
**Data source(s):** git_commits
**Privacy class:** individual
**Granularity:** period

## Definition

The average number of commits authored by the user per ISO calendar week within the selected date window. This metric is used as a DORA Deployment Frequency proxy in the absence of CI/CD pipeline data. A higher average indicates more frequent integration of work into the shared codebase. Because the platform does not distinguish commits on main vs. feature branches, this metric counts all commits, making it a commit-frequency signal rather than a strict deployment-frequency signal. (Forsgren, Humble & Kim, 2018.)

## Formula

```
FOR each commit C by user in [from, to):
  week_key(C) = ISO_WEEK_YEAR(C.author_date) + "-W" + ZERO_PAD_2(ISO_WEEK(C.author_date))
  -- e.g. "2025-W03"

commits_per_week = MAP(week_key → SUM(commits on that day))
  FROM (
    SELECT CAST(author_date AS date) AS day,
           COUNT(*)                   AS daily_count
    FROM git_commits
    WHERE repository_id IN :repoIds
      AND author_email  = user.email
      AND author_date  >= from AND author_date < to+1
      AND author_name NOT LIKE '%[bot]%'
    GROUP BY day
  )

merge_to_main_frequency_per_week =
  AVG(commits_per_week.values())
```

Java implementation:
```java
Map<String, Long> byWeek = new TreeMap<>();
for (DailyCommitsProjection row : rows) {
    if (row.getCommitsCount() == 0) continue;
    LocalDate day = row.getDay().toLocalDate();
    int weekYear = day.get(WeekFields.ISO.weekBasedYear());
    int weekNum  = day.get(WeekFields.ISO.weekOfWeekBasedYear());
    String key = weekYear + "-W" + String.format("%02d", weekNum);
    byWeek.merge(key, row.getCommitsCount(), Long::sum);
}
double avgPerWeek = byWeek.values().stream().mapToLong(Long::longValue).average().orElse(0.0);
```

- Attribution: `author_email = user.email`.
- Bot exclusion: `author_name NOT LIKE '%[bot]%'`.
- ISO week: a week that crosses year boundaries (e.g. last days of December may belong to week 1 of the next year) is counted by ISO standard using `WeekFields.ISO`.
- Partial weeks at the boundaries of the date window are included at their actual commit count (not prorated).
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`, `repository = null` (sum across all repos).

## Edge cases

- **No commits in window**: `rows.isEmpty()` → early return; no snapshot written.
- **Single-week window**: average equals the commit count for that week exactly.
- **Partial weeks at window boundaries**: a window from Wednesday to Wednesday includes two partial ISO weeks; both are counted by their actual commit count. The average reflects partial-week contributions without proration.
- **Weeks with zero commits**: a week where the user made no commits produces no entry in `byWeek` and does not drag the average down — only active weeks are averaged. This means the metric reflects commitment intensity during active periods, not calendar regularity.

## Validation (thesis §8.3)

- **Expected range**: 5–25 commits/week for an active developer (high DORA performers in Accelerate commit multiple times per day); <3/week may indicate infrequent integration habits.
- **Comparison baseline**: `git log --author=<email> --after=<from> --before=<to> --format="%ad" --date=format:"%G-W%V"` — group by ISO week and count.
- **Controlled-change test**: make 10 commits in week 1 and 20 commits in week 2 of a 2-week window → metric must equal 15.0.

## References

Forsgren, N., Humble, J., & Kim, G. (2018). *Accelerate: The Science of Lean Software and DevOps*. IT Revolution. (DORA Deployment Frequency definition and elite performer benchmarks.)
