# Focus Ratio (Days with Tasks)

**Framework:** SPACE
**Category:** Focus / Flow
**Unit:** ratio (0.0–1.0)
**Data source(s):** git_commits
**Privacy class:** individual
**Granularity:** period

## Definition

The proportion of working days (Monday through Friday) in the selected date window on which the user made at least one commit. A value of 1.0 means the user committed every weekday; 0.5 means every other weekday. This metric is a proxy for the SPACE "Flow" dimension — sustained, interruption-free coding work tends to produce commits across consecutive weekdays, whereas context-switching, meetings, or planning-heavy periods produce commit-free days. (Forsgren et al., 2021.)

## Formula

```
weekdays_with_commits =
  COUNT(DISTINCT CAST(author_date AS date))
  FROM git_commits
  WHERE repository_id IN :repoIds
    AND author_email  = user.email
    AND author_date  >= from
    AND author_date   < to+1
    AND DAYOFWEEK(author_date IN user.timezone) NOT IN (SATURDAY, SUNDAY)
    AND author_name NOT LIKE '%[bot]%'

total_weekdays_in_window =
  COUNT(calendar days d in [from, to]
    WHERE DAYOFWEEK(d) NOT IN (SATURDAY, SUNDAY))

focus_ratio = weekdays_with_commits / total_weekdays_in_window
```

**Storage model**: the calculation engine does **not** store the ratio directly. Instead, for each weekday with ≥1 commit, it saves a `MetricSnapshot` with `value = 1.0` and `metricType = FOCUS_RATIO_DAYS_TASKS`. Zero-commit weekdays produce no row. The ratio is computed on the **read side** by `MetricsController`:

```
focus_ratio = COUNT(FOCUS_RATIO_DAYS_TASKS snapshots in [from, to])
              / COUNT(weekdays in [from, to])
```

This design avoids writing O(window_days) rows per user per recalculation cycle.

- Attribution: `author_email = user.email`.
- Weekday check applied in the service (`DayOfWeek != SATURDAY && DayOfWeek != SUNDAY`) using the commit's UTC-normalised date.
- Snapshots have `periodFrom = null`, `periodTo = null` (stored as daily shape, one row per active weekday).
- Bot exclusion: only `author_email` is checked; bot emails do not match `user.email` in practice.

## Edge cases

- **Window with zero weekdays** (e.g. a 2-day weekend selection): denominator = 0 → the controller returns null to avoid divide-by-zero.
- **All commits on weekends**: zero snapshots saved → ratio = 0.0 (0 / total weekdays).
- **No commits in window**: no snapshots saved → ratio = 0.0 (read side computes 0 / total weekdays).
- **Date range spanning partial weeks**: all weekdays in the range are counted, including the first and last day if they are weekdays.

## Validation (thesis §8.3)

- **Expected range**: 0.4–0.9 for active developers; <0.3 may indicate a planning-heavy sprint or extended leave; 1.0 is achievable in focused coding sprints.
- **Comparison baseline**: `git log --author=<email> --after=<from> --before=<to> --format="%ad" --date=format:"%A"` — count distinct weekday dates.
- **Controlled-change test**: make commits on exactly 3 out of 5 weekdays in a specific week → `focus_ratio` for that week must equal 0.6.

## References

Forsgren, N., et al. (2021). The SPACE of developer productivity. *Queue*, 19(1), 20–48. (Flow / Focus dimension.)
