package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.StatsStatus;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.CommitDetailProjection;
import com.juliashtal.devanalytics.metrics.model.DailyChurnProjection;
import com.juliashtal.devanalytics.metrics.model.DailyCommitsProjection;
import com.juliashtal.devanalytics.metrics.model.DailyCountProjection;
import com.juliashtal.devanalytics.metrics.model.IssueLeadTimeProjection;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.metrics.model.PrLeadTimeProjection;
import com.juliashtal.devanalytics.metrics.model.PrReviewTimestampProjection;
import com.juliashtal.devanalytics.metrics.model.RepoCountProjection;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

import static com.juliashtal.devanalytics.metrics.model.MetricType.*;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricSnapshotRepository repository;
    private final GitCommitEntityRepository commitRepository;
    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitHubPrReviewRepository prReviewRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final GitRepositoryEntityRepository gitRepoRepository;
    private final UserRepoRegistrationRepository userRepoRegRepository;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Personal metrics — uses only the user's own data sources, saved with team=null. */
    @Transactional
    public void calculateDailyMetrics(Long userId, LocalDate fromDate, LocalDate toDate) {
        User user = userRepository.getReferenceById(userId);
        calculateDailyMetricsForUser(user, null, fromDate, toDate);
    }

    /**
     * Team-scoped metrics — uses only repos belonging to this specific team,
     * attributed by author identity. Saved with team=team so they are isolated
     * from the user's personal metrics and from other teams.
     */
    @Transactional
    public void calculateForTeam(Long teamId, Long requestingUserId, LocalDate fromDate, LocalDate toDate) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        Role role = userRepository.getReferenceById(requestingUserId).getRole();
        if (role != Role.ADMIN && !team.getManager().getId().equals(requestingUserId)) {
            throw new ForbiddenException("Only the team manager or an admin can trigger team calculation");
        }

        for (User member : team.getMembers()) {
            calculateDailyMetricsForUser(member, team, fromDate, toDate);
        }
    }

    // -------------------------------------------------------------------------
    // Core calculation — dispatches personal vs. team path
    // -------------------------------------------------------------------------

    private void calculateDailyMetricsForUser(User user, Team team, LocalDate fromDate, LocalDate toDate) {
        Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // For personal metrics: repos the user explicitly registered.
        // For team metrics: repos registered under the team's data sources.
        // Fall back to the user's own repos when the team has no team-scoped data
        // sources yet — this is the common case when repos are user-scoped.
        List<Long> repoIds;
        if (team != null) {
            repoIds = gitRepoRepository.findIdsByTeamIds(List.of(team.getId()));
            if (repoIds.isEmpty()) {
                repoIds = userRepoRegRepository.findRepoIdsByUserId(user.getId());
            }
        } else {
            // Personal: use explicitly registered repos.
            // Fall back to repos from the user's team memberships so that a developer
            // who has never manually registered repos still sees their own commits.
            repoIds = userRepoRegRepository.findRepoIdsByUserId(user.getId());
            if (repoIds.isEmpty()) {
                List<Long> memberTeamIds = teamRepository.findByMembersId(user.getId())
                        .stream().map(Team::getId).toList();
                if (!memberTeamIds.isEmpty()) {
                    repoIds = gitRepoRepository.findIdsByTeamIds(memberTeamIds);
                }
            }
        }

        calcDailyCommits(user, team, repoIds, from, to);
        calcDailyPrs(user, team, repoIds, from, to);
        calcDailyIssues(user, team, repoIds, from, to);
        calcDailyChurn(user, team, repoIds, from, to);
        calcLeadTimePrs(user, team, repoIds, fromDate, toDate, from, to);
        calcLeadTimeIssues(user, team, repoIds, fromDate, toDate, from, to);
        calcLeadTimeFirstCommitToMerge(user, team, repoIds, fromDate, toDate, from, to);
        calcReviewResponseTime(user, team, repoIds, fromDate, toDate, from, to);
        calcFocusRatio(user, team, repoIds, fromDate, toDate, from, to);
        calcAfterHoursRatioAndRefactorRatio(user, team, repoIds, fromDate, toDate, from, to);
        calcDeepWorkStreak(user, team, repoIds, fromDate, toDate, from, to);
        calcMergeToMainFrequency(user, team, repoIds, fromDate, toDate, from, to);
        calcKnowledgeSilo(user, team, repoIds, fromDate, toDate, from, to);
        calcPrSizeComplexity(user, team, repoIds, fromDate, toDate, from, to);
        calcMergeWithoutReview(user, team, repoIds, fromDate, toDate, from, to);
    }

    // -------------------------------------------------------------------------
    // Metric calculations
    // Attribution is ALWAYS by author identity — team only controls where the
    // snapshot is saved (personal=null vs team-scoped=team).
    // This prevents a manager who registered a shared repo from seeing all
    // developers' commits credited to their own personal dashboard.
    // -------------------------------------------------------------------------

    private void calcDailyCommits(User user, Team team, List<Long> repoIds,
                                  Instant from, Instant to) {
        if (repoIds.isEmpty()) return;

        // Always filter by author email so only the user's own commits are counted.
        List<DailyCommitsProjection> rows = new ArrayList<>(commitRepository
                .aggregateCommitsDailyByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to));

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (DailyCommitsProjection row : rows) {
            LocalDate day  = row.getDay().toLocalDate();
            Long repoId    = row.getRepoId();
            long count     = row.getCommitsCount();
            double avgSize = row.getAvgSize() != null ? row.getAvgSize() : 0.0;

            GitRepositoryEntity repo = repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById);
            saveMetric(user, team, day, DAILY_COMMITS_COUNT, count, repo, null, null);
            saveMetric(user, team, day, DAILY_COMMITS_AVG_SIZE, avgSize, repo, null, null);
        }
    }

    private void calcDailyPrs(User user, Team team, List<Long> repoIds,
                              Instant from, Instant to) {
        if (repoIds.isEmpty()) return;

        // GitHub PRs are attributed by login. Skip if not configured — the user
        // can set their GitHub login in Settings to enable PR metrics.
        if (user.getGithubLogin() == null) return;

        List<DailyCountProjection> createdRows = new ArrayList<>(pullRequestRepository
                .aggregatePrCreatedDailyByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to));
        List<DailyCountProjection> mergedRows  = new ArrayList<>(pullRequestRepository
                .aggregatePrMergedDailyByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to));

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (DailyCountProjection row : createdRows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long count    = row.getCount();
            saveMetric(user, team, day, DAILY_PR_CREATED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }

        for (DailyCountProjection row : mergedRows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long count    = row.getCount();
            saveMetric(user, team, day, DAILY_PR_MERGED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }
    }

    private void calcDailyIssues(User user, Team team, List<Long> repoIds,
                                 Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        // Issues are project-level — no author filter for either personal or team path
        List<DailyCountProjection> createdRows = new ArrayList<>(issueRepository.aggregateIssuesCreatedDailyByRepoIds(repoIds, from, to));
        List<DailyCountProjection> closedRows  = new ArrayList<>(issueRepository.aggregateIssuesClosedDailyByRepoIds(repoIds, from, to));

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (DailyCountProjection row : createdRows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long count    = row.getCount();
            saveMetric(user, team, day, DAILY_ISSUES_CREATED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }

        for (DailyCountProjection row : closedRows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long count    = row.getCount();
            saveMetric(user, team, day, DAILY_ISSUES_CLOSED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }
    }

    private void calcDailyChurn(User user, Team team, List<Long> repoIds,
                                Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        // Always filter by author email — churn should reflect the user's own code changes.
        List<DailyChurnProjection> rows = new ArrayList<>(commitRepository
                .aggregateChurnDailyByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to));

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (DailyChurnProjection row : rows) {
            LocalDate day = row.getDay().toLocalDate();
            Long repoId   = row.getRepoId();
            long add      = row.getAdditions();
            long del      = row.getDeletions();

            long total = add + del;
            double churn = total > 0 ? (double) del / total : 0.0;
            saveMetric(user, team, day, DAILY_CHURN_RATIO, churn,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }
    }

    private void calcLeadTimePrs(User user, Team team, List<Long> repoIds,
                                 LocalDate fromDate, LocalDate toDate, Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        if (user.getGithubLogin() == null) return;
        List<PrLeadTimeProjection> rows = new ArrayList<>(pullRequestRepository
                .findMergedLeadTimesByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to));

        Map<Long, List<Long>> perRepo = new HashMap<>();

        for (PrLeadTimeProjection row : rows) {
            Long repoId     = row.getRepoId();
            Instant created = row.getCreatedAt();
            Instant merged  = row.getMergedAt();
            long hours = Duration.between(created, merged).toHours();
            perRepo.computeIfAbsent(repoId, id -> new ArrayList<>()).add(hours);
        }

        perRepo.forEach((repoId, values) -> {
            Collections.sort(values);
            saveMetric(user, team, fromDate, PR_LEAD_TIME_HOURS_MEDIAN, medianOfLongs(values),
                    gitRepoRepository.getReferenceById(repoId), fromDate, toDate);
        });
    }

    private void calcLeadTimeIssues(User user, Team team, List<Long> repoIds,
                                    LocalDate fromDate, LocalDate toDate, Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        // Issues are project-level — no author filter for either path
        List<IssueLeadTimeProjection> rows = new ArrayList<>(issueRepository.findIssueLeadTimesByRepoIds(repoIds, from, to));

        Map<Long, List<Long>> perRepo = new HashMap<>();

        for (IssueLeadTimeProjection row : rows) {
            Long repoId     = row.getRepoId();
            Instant created = row.getCreatedAt();
            Instant closed  = row.getClosedAt();
            long hours = Duration.between(created, closed).toHours();
            perRepo.computeIfAbsent(repoId, id -> new ArrayList<>()).add(hours);
        }

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        perRepo.forEach((repoId, values) -> {
            Collections.sort(values);
            saveMetric(user, team, fromDate, ISSUE_LEAD_TIME_HOURS_MEDIAN, medianOfLongs(values),
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), fromDate, toDate);
        });
    }

    private void calcLeadTimeFirstCommitToMerge(User user, Team team, List<Long> repoIds,
                                                LocalDate fromDate, LocalDate toDate,
                                                Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        if (user.getGithubLogin() == null) return;
        List<GitHubPullRequestEntity> prs = new ArrayList<>(pullRequestRepository
                .findMergedPrsByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to));

        Map<Long, List<Long>> perRepo = new HashMap<>();

        for (GitHubPullRequestEntity pr : prs) {
            List<GitCommitEntity> commits =
                    commitRepository.findCommitsForPr(pr.getRepository(), pr.getNumber());
            if (commits.isEmpty() || pr.getMergedAt() == null) continue;

            long hours = Duration.between(commits.get(0).getAuthorDate(), pr.getMergedAt()).toHours();
            pr.setLeadTimeHours(hours);
            perRepo.computeIfAbsent(pr.getRepository().getId(), id -> new ArrayList<>()).add(hours);
        }
        pullRequestRepository.saveAll(prs);

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        perRepo.forEach((repoId, hours) -> {
            Collections.sort(hours);
            saveMetric(user, team, fromDate, PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN,
                    medianOfLongs(hours), repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), fromDate, toDate);
        });
    }

    private void calcReviewResponseTime(User user, Team team, List<Long> repoIds,
                                         LocalDate fromDate, LocalDate toDate, Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        if (user.getGithubLogin() == null) return;

        List<GitHubPullRequestEntity> prs = new ArrayList<>(pullRequestRepository
                .findMergedPrsByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to));
        if (prs.isEmpty()) return;

        List<Long> prIds = prs.stream().map(GitHubPullRequestEntity::getId).toList();
        Map<Long, Instant> firstReviewByPrId = new HashMap<>();
        for (PrReviewTimestampProjection row : prReviewRepository.findFirstReviewTimestampsByPrIds(prIds)) {
            firstReviewByPrId.put(row.getPrId(), row.getReviewedAt());
        }

        Map<Long, List<Long>> perRepo = new HashMap<>();
        for (GitHubPullRequestEntity pr : prs) {
            Instant firstReview = firstReviewByPrId.get(pr.getId());
            if (firstReview == null || pr.getCreatedAt() == null) continue;
            long hours = Duration.between(pr.getCreatedAt(), firstReview).toHours();
            if (hours < 0) continue;
            perRepo.computeIfAbsent(pr.getRepository().getId(), id -> new ArrayList<>()).add(hours);
        }

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        perRepo.forEach((repoId, values) -> {
            Collections.sort(values);
            saveMetric(user, team, fromDate, REVIEW_RESPONSE_TIME_HOURS_MEDIAN, medianOfLongs(values),
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), fromDate, toDate);
        });
    }

    private void calcFocusRatio(User user, Team team, List<Long> repoIds,
                                LocalDate fromDate, LocalDate toDate, Instant from, Instant to) {
        if (repoIds.isEmpty()) return;

        // Always filter by author email — focus ratio reflects the user's own active days.
        List<DailyCommitsProjection> rows = new ArrayList<>(commitRepository
                .aggregateCommitsDailyByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to));

        Set<LocalDate> daysWithCommits = new HashSet<>();
        for (DailyCommitsProjection row : rows) {
            if (row.getCommitsCount() > 0) daysWithCommits.add(row.getDay().toLocalDate());
        }

        // Only persist days with commits (value = 1.0). Zero-commit weekdays are not
        // stored, which avoids O(range_days) DB writes. The aggregate endpoint computes
        // the ratio as saved_count / total_weekdays on the read side.
        for (LocalDate day : daysWithCommits) {
            DayOfWeek dow = day.getDayOfWeek();
            boolean inRange = !day.isBefore(fromDate) && !day.isAfter(toDate);
            boolean isWeekday = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            if (inRange && isWeekday) {
                saveMetric(user, team, day, FOCUS_RATIO_DAYS_TASKS, 1.0, null, null, null);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Wellness + Quality metric calculations
    // -------------------------------------------------------------------------

    /**
     * AFTER_HOURS_COMMIT_RATIO — fraction of commits outside 09:00–18:00 Mon–Fri
     * in the user's configured timezone.
     *
     * REFACTOR_RATIO — fraction of commits where deletions > additions, a proxy
     * for refactoring / code-reduction work.
     *
     * Both are computed from the same query to avoid a duplicate DB round-trip.
     */
    private void calcAfterHoursRatioAndRefactorRatio(User user, Team team, List<Long> repoIds,
                                                      LocalDate fromDate, LocalDate toDate,
                                                      Instant from, Instant to) {
        if (repoIds.isEmpty()) return;

        List<CommitDetailProjection> rows = commitRepository
                .findCommitDetailsByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to);
        if (rows.isEmpty()) return;

        ZoneId zone;
        try {
            zone = ZoneId.of(user.getTimezone() != null ? user.getTimezone() : "UTC");
        } catch (Exception e) {
            zone = ZoneOffset.UTC;
        }

        long total = rows.size();
        long outOfHours = 0;
        long refactorCount = 0;
        long enrichedTotal = 0; // commits with real diff stats (statsStatus = COMPLETE)

        for (CommitDetailProjection row : rows) {
            Instant authorDate  = row.getAuthorDate();
            int additions       = row.getAdditions();
            int deletions       = row.getDeletions();
            StatsStatus statsStatus = row.getStatsStatus();

            // AFTER_HOURS uses authorDate only — always reliable regardless of enrichment status
            ZonedDateTime zdt = authorDate.atZone(zone);
            DayOfWeek dow = zdt.getDayOfWeek();
            int hour = zdt.getHour();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean isWorkHours = hour >= 9 && hour < 18;
            if (isWeekend || !isWorkHours) outOfHours++;

            // REFACTOR_RATIO only counts commits where diff stats have been enriched.
            // PENDING/FAILED GitHub commits have additions=0 and deletions=0 as placeholders,
            // which would make the ratio artificially zero until enrichment completes.
            if (statsStatus == StatsStatus.COMPLETE) {
                enrichedTotal++;
                if (deletions > additions) refactorCount++;
            }
        }

        saveMetric(user, team, fromDate, AFTER_HOURS_COMMIT_RATIO,
                (double) outOfHours / total, null, fromDate, toDate);

        // Only persist REFACTOR_RATIO once there is at least one enriched commit.
        // This avoids saving a misleading 0.0 when all commits are still PENDING.
        if (enrichedTotal > 0) {
            saveMetric(user, team, fromDate, REFACTOR_RATIO,
                    (double) refactorCount / enrichedTotal, null, fromDate, toDate);
        }
    }

    /**
     * DEEP_WORK_STREAK_DAYS — longest consecutive-day run where the user had ≥1 commit.
     * Saved as a single aggregate value for the window.
     */
    private void calcDeepWorkStreak(User user, Team team, List<Long> repoIds,
                                    LocalDate fromDate, LocalDate toDate,
                                    Instant from, Instant to) {
        if (repoIds.isEmpty()) return;

        List<DailyCommitsProjection> rows = commitRepository
                .aggregateCommitsDailyByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to);

        // Collect unique days that have at least one commit, sorted ascending.
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

        saveMetric(user, team, fromDate, DEEP_WORK_STREAK_DAYS, maxStreak, null, fromDate, toDate);
    }

    /**
     * MERGE_TO_MAIN_FREQUENCY_PER_WEEK — average commit count per ISO calendar week.
     * This is a DORA deployment-frequency proxy when no CI/CD pipeline data is available.
     * All commits are counted (branch detection is not available in the current data model).
     */
    private void calcMergeToMainFrequency(User user, Team team, List<Long> repoIds,
                                          LocalDate fromDate, LocalDate toDate,
                                          Instant from, Instant to) {
        if (repoIds.isEmpty()) return;

        List<DailyCommitsProjection> rows = commitRepository
                .aggregateCommitsDailyByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to);
        if (rows.isEmpty()) return;

        // Group daily totals by ISO week key so partial weeks are still counted.
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
        saveMetric(user, team, fromDate, MERGE_TO_MAIN_FREQUENCY_PER_WEEK,
                avgPerWeek, null, fromDate, toDate);
    }

    /**
     * KNOWLEDGE_SILO_SCORE — the maximum fraction of commits belonging to this user
     * across all their repos in the window. A score of 0.9 means they authored 90%
     * of commits to at least one repo — a strong bus-factor signal.
     */
    private void calcKnowledgeSilo(User user, Team team, List<Long> repoIds,
                                   LocalDate fromDate, LocalDate toDate,
                                   Instant from, Instant to) {
        if (repoIds.isEmpty()) return;

        Map<Long, Long> totalByRepo = new HashMap<>();
        for (RepoCountProjection row : commitRepository.countTotalCommitsByRepoIds(repoIds, from, to)) {
            totalByRepo.put(row.getRepoId(), row.getCount());
        }
        if (totalByRepo.isEmpty()) return;

        Map<Long, Long> userByRepo = new HashMap<>();
        for (RepoCountProjection row : commitRepository
                .countCommitsByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to)) {
            userByRepo.put(row.getRepoId(), row.getCount());
        }

        double maxShare = 0.0;
        for (Map.Entry<Long, Long> entry : totalByRepo.entrySet()) {
            long total = entry.getValue();
            if (total == 0) continue;
            long userCount = userByRepo.getOrDefault(entry.getKey(), 0L);
            double share = (double) userCount / total;
            if (share > maxShare) maxShare = share;
        }

        saveMetric(user, team, fromDate, KNOWLEDGE_SILO_SCORE, maxShare, null, fromDate, toDate);
    }

    /**
     * PR_SIZE_COMPLEXITY_SCORE — median of (additions + deletions) / commitsCount
     * across merged PRs authored by this user. Guards against commitsCount = 0
     * (treats those PRs as "squash merged" — total size counts as 1-commit diff).
     * Requires githubLogin to be set.
     */
    private void calcPrSizeComplexity(User user, Team team, List<Long> repoIds,
                                      LocalDate fromDate, LocalDate toDate,
                                      Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        if (user.getGithubLogin() == null) return;

        List<GitHubPullRequestEntity> prs = pullRequestRepository
                .findMergedPrsByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to);
        if (prs.isEmpty()) return;

        Map<Long, List<Double>> perRepo = new HashMap<>();
        for (GitHubPullRequestEntity pr : prs) {
            int size = pr.getAdditions() + pr.getDeletions();
            int commits = pr.getCommitsCount() > 0 ? pr.getCommitsCount() : 1;
            double complexity = (double) size / commits;
            perRepo.computeIfAbsent(pr.getRepository().getId(), id -> new ArrayList<>()).add(complexity);
        }

        perRepo.forEach((repoId, values) -> {
            Collections.sort(values);
            int n = values.size();
            double median = n % 2 == 1
                    ? values.get(n / 2)
                    : (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
            saveMetric(user, team, fromDate, PR_SIZE_COMPLEXITY_SCORE, median,
                    gitRepoRepository.getReferenceById(repoId), fromDate, toDate);
        });
    }

    /**
     * MERGE_WITHOUT_REVIEW_RATIO — fraction of merged PRs authored by this user
     * that had zero reviews recorded in github_pr_reviews.
     * Requires githubLogin to be set and PR review data to have been collected.
     */
    private void calcMergeWithoutReview(User user, Team team, List<Long> repoIds,
                                        LocalDate fromDate, LocalDate toDate,
                                        Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        if (user.getGithubLogin() == null) return;

        List<GitHubPullRequestEntity> prs = pullRequestRepository
                .findMergedPrsByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to);
        if (prs.isEmpty()) return;

        List<Long> prIds = prs.stream().map(GitHubPullRequestEntity::getId).toList();
        Set<Long> prsWithReviews = prReviewRepository.findFirstReviewTimestampsByPrIds(prIds)
                .stream()
                .map(PrReviewTimestampProjection::getPrId)
                .collect(Collectors.toSet());

        Map<Long, long[]> perRepo = new HashMap<>(); // repoId -> [noReviewCount, totalCount]
        for (GitHubPullRequestEntity pr : prs) {
            long[] counts = perRepo.computeIfAbsent(pr.getRepository().getId(), id -> new long[]{0, 0});
            counts[1]++;
            if (!prsWithReviews.contains(pr.getId())) counts[0]++;
        }

        perRepo.forEach((repoId, counts) -> {
            double ratio = counts[1] > 0 ? (double) counts[0] / counts[1] : 0.0;
            saveMetric(user, team, fromDate, MERGE_WITHOUT_REVIEW_RATIO, ratio,
                    gitRepoRepository.getReferenceById(repoId), fromDate, toDate);
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void saveMetric(User user,
                            Team team,
                            LocalDate date,
                            MetricType metricType,
                            double value,
                            GitRepositoryEntity repo,
                            LocalDate periodFrom,
                            LocalDate periodTo) {
        Long teamId = team != null ? team.getId() : null;
        Long repoId = repo  != null ? repo.getId()  : null;

        MetricSnapshot snapshot = repository
                .findExisting(user.getId(), teamId, repoId, date, metricType.name(), periodFrom, periodTo)
                .orElseGet(MetricSnapshot::new);

        snapshot.setUser(user);
        snapshot.setTeam(team);
        snapshot.setRepository(repo);
        snapshot.setDate(date);
        snapshot.setMetricType(metricType);
        snapshot.setPeriodFrom(periodFrom);
        snapshot.setPeriodTo(periodTo);
        snapshot.setValue(value);

        repository.save(snapshot);
    }

    private double medianOfLongs(List<Long> values) {
        int n = values.size();
        if (n == 0) return 0.0;
        if (n % 2 == 1) return values.get(n / 2);
        return (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
    }
}
