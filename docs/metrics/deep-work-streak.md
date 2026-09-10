# Deep Work Streak

**Framework:** SPACE
**Category:** Focus / Flow
**Unit:** days (longest consecutive run)
**Data source(s):** git_commits
**Privacy class:** individual
**Granularity:** period

## Definition

The longest unbroken run of consecutive calendar days on which the user made at least one commit, within the selected date window. A longer streak indicates sustained, day-over-day focused coding activity — a strong signal of the "flow" state described in the SPACE framework. A streak resets whenever a calendar day (including weekends and holidays) passes with no commits. (Forsgren et al., 2021.)

## Formula

```
commit_days = SORTED SET OF DISTINCT CAST(author_date AS date)
  FROM git_commits
  WHERE repository_id IN :repoIds
    AND ( author_github_id = user.githubUserId
       OR lower(author_email) IN user.commitEmails )
    AND author_date  >= from
    AND author_date   < to+1
    AND author_name NOT LIKE '%[bot]%'

deep_work_streak_days = MAX consecutive run in commit_days
  where "consecutive" means day[i+1] = day[i] + 1 day
```

Implementation (Java):
```java
TreeSet<LocalDate> days = ...;  // sorted ascending
int maxStreak = 0, streak = 0;
LocalDate prev = null;
for (LocalDate day : days) {
    streak = (prev != null && day.equals(prev.plusDays(1))) ? streak + 1 : 1;
    maxStreak = Math.max(maxStreak, streak);
    prev = day;
}
```

- Attribution: `author_github_id = user.githubUserId` **OR** `lower(author_email) IN user.commitEmails`. Either path alone is sufficient; a commit matching both is counted once. See [author-attribution.md](author-attribution.md).
- Bot exclusion: `author_name NOT LIKE '%[bot]%'`.
- **Weekends and holidays count against the streak**: a commit-free Saturday breaks a Monday–Friday streak, resulting in a maximum possible 5-day streak in a standard work week without weekend commits.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`, `repository = null` (across all repos combined).

## Edge cases

- **No commits in window**: `daysWithCommits` is empty → early return; no snapshot written.
- **Single commit day**: streak = 1.
- **Weekend commits**: if the user commits on Saturday and Sunday, those days count; a Mon–Sun streak of 7 is possible.
- **Window boundary**: only days within `[from, to]` are considered; a commit the day before `from` does not extend a streak that starts on `from`.

## Validation (thesis §8.3)

- **Expected range**: 1–10 days for typical sprints; >14 days indicates either very high commit frequency or weekend/holiday commits.
- **Comparison baseline**: `git log --author=<email> --after=<from> --before=<to> --format="%ad" --date=short | sort -u` — manually find the longest run of consecutive dates.
- **Controlled-change test**: commit on days 1, 2, 3, 5, 6 of a window (skipping day 4) → streak must equal 3 (days 1–3), not 5.

## References

Forsgren, N., et al. (2021). The SPACE of developer productivity. *Queue*, 19(1), 20–48. (Flow dimension.)
