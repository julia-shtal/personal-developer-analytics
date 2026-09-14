# After-Hours Commit Ratio

**Framework:** SPACE
**Category:** Satisfaction / Wellness
**Unit:** ratio (0.0–1.0)
**Data source(s):** git_commits
**Privacy class:** individual
**Granularity:** period

## Definition

The fraction of commits authored by the user that were committed outside normal business hours — defined as before 09:00 or at or after 18:00 on weekdays, or any time on weekends — using the user's configured timezone. A higher ratio indicates more work happening outside standard working hours, which is a wellbeing risk signal. The metric is computed for the full date window and saved as a single aggregate snapshot. Timestamps are always interpreted in the user's configured timezone (`User.timezone`), defaulting to UTC if the timezone is invalid or unset. (Forsgren et al., 2021.)

## Formula

```
after_hours_commit_ratio =
  COUNT(commits where is_after_hours(author_date, user.timezone))
  / COUNT(all commits by user in [from, to))

is_after_hours(author_date, timezone):
  zdt = author_date.atZone(ZoneId.of(timezone))
  RETURN zdt.dayOfWeek IN {SATURDAY, SUNDAY}
      OR zdt.hour < 9
      OR zdt.hour >= 18
```

- Attribution: `author_github_id = user.githubUserId` **OR** `lower(author_email) IN user.commitEmails`. Either path alone is sufficient; a commit matching both is counted once. See [author-attribution.md](author-attribution.md).
- Timezone: resolved once by `UserZone.of(user)` — falls back to `ZoneOffset.UTC` on parse failure. This is the attribution clock; see [timezone.md](timezone.md).
- Business hours definition: 09:00 (inclusive) to 18:00 (exclusive), Monday through Friday.
- Denominator: all commits by the user in the window, including after-hours ones. No denominator guard for zero-commits: if `rows.isEmpty()`, the method returns early and no snapshot is saved.
- Bot exclusion: `author_name NOT LIKE '%[bot]%'`, applied in the query. Attribution can match on a declared email address, and a local commit carries no `author_github_id`, so an automation account configured with the user's address would otherwise be attributed to them. See [author-attribution.md](author-attribution.md).
- Computed alongside `REFACTOR_RATIO` in a single `findCommitDetailsByRepoIdsAndIdentity` query to avoid a duplicate DB round-trip.
- Saved as aggregate shape: `periodFrom = fromDate`, `periodTo = toDate`.

## Edge cases

- **`user.timezone` is invalid**: falls back to `ZoneOffset.UTC`, with a warning logged. The column is `NOT NULL DEFAULT 'Europe/Berlin'`, so a user who never opens Settings is classified in Berlin's working hours rather than UTC — the fallback is reached only by an unparseable value. Users outside that zone should set their timezone for accurate results.
- **No commits in window**: method returns early; no snapshot saved.
- **Timezone changed after computation**: stored snapshots are not recomputed, so a series can contain days classified under two different zones. See [timezone.md](timezone.md).
- **Commits spanning timezone DST transitions**: `ZonedDateTime` handles DST correctly — Java's `ZoneId` applies the correct offset for each timestamp.
- **Developers in non-standard time zones**: the 09:00–18:00 window is fixed; it does not adapt to local customs (e.g. a developer in a culture with different business hours). This is a known limitation.

## Validation (thesis §8.3)

- **Expected range**: 0.0–0.3 for a developer working standard hours; >0.5 may indicate sustained overtime or a developer in a different timezone than configured.
- **Comparison baseline**: `git log --author=<email>` — manually classify each commit's local time using the user's timezone.
- **Controlled-change test**: push 3 commits at 22:00 local time and 1 commit at 10:00 local time → `after_hours_commit_ratio` must equal 0.75.

## References

Forsgren, N., et al. (2021). The SPACE of developer productivity. *Queue*, 19(1), 20–48. (Satisfaction and wellbeing dimension.)
