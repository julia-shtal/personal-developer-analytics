package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.exception.ForbiddenException;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.metrics.model.MetricSnapshot;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

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
        List<Object[]> rows = new ArrayList<>(commitRepository
                .aggregateCommitsDailyByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to));

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day  = ((java.sql.Date) row[0]).toLocalDate();
            Long repoId    = ((Number) row[1]).longValue();
            long count     = ((Number) row[2]).longValue();
            double avgSize = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

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

        List<Object[]> createdRows = new ArrayList<>(pullRequestRepository
                .aggregatePrCreatedDailyByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to));
        List<Object[]> mergedRows  = new ArrayList<>(pullRequestRepository
                .aggregatePrMergedDailyByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to));

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (Object[] row : createdRows) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            Long repoId   = ((Number) row[1]).longValue();
            long count    = ((Number) row[2]).longValue();
            saveMetric(user, team, day, DAILY_PR_CREATED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }

        for (Object[] row : mergedRows) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            Long repoId   = ((Number) row[1]).longValue();
            long count    = ((Number) row[2]).longValue();
            saveMetric(user, team, day, DAILY_PR_MERGED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }
    }

    private void calcDailyIssues(User user, Team team, List<Long> repoIds,
                                 Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        // Issues are project-level — no author filter for either personal or team path
        List<Object[]> createdRows = new ArrayList<>(issueRepository.aggregateIssuesCreatedDailyByRepoIds(repoIds, from, to));
        List<Object[]> closedRows  = new ArrayList<>(issueRepository.aggregateIssuesClosedDailyByRepoIds(repoIds, from, to));

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (Object[] row : createdRows) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            Long repoId   = ((Number) row[1]).longValue();
            long count    = ((Number) row[2]).longValue();
            saveMetric(user, team, day, DAILY_ISSUES_CREATED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }

        for (Object[] row : closedRows) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            Long repoId   = ((Number) row[1]).longValue();
            long count    = ((Number) row[2]).longValue();
            saveMetric(user, team, day, DAILY_ISSUES_CLOSED, count,
                    repoCache.computeIfAbsent(repoId, gitRepoRepository::getReferenceById), null, null);
        }
    }

    private void calcDailyChurn(User user, Team team, List<Long> repoIds,
                                Instant from, Instant to) {
        if (repoIds.isEmpty()) return;
        // Always filter by author email — churn should reflect the user's own code changes.
        List<Object[]> rows = new ArrayList<>(commitRepository
                .aggregateChurnDailyByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to));

        Map<Long, GitRepositoryEntity> repoCache = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            Long repoId   = ((Number) row[1]).longValue();
            long add      = ((Number) row[2]).longValue();
            long del      = ((Number) row[3]).longValue();

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
        List<Object[]> rows = new ArrayList<>(pullRequestRepository
                .findMergedLeadTimesByRepoIdsAndAuthorLogin(repoIds, user.getGithubLogin(), from, to));

        Map<Long, List<Long>> perRepo = new HashMap<>();

        for (Object[] row : rows) {
            Long repoId  = ((Number) row[0]).longValue();
            Instant created = (Instant) row[1];
            Instant merged  = (Instant) row[2];
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
        List<Object[]> rows = new ArrayList<>(issueRepository.findIssueLeadTimesByRepoIds(repoIds, from, to));

        Map<Long, List<Long>> perRepo = new HashMap<>();

        for (Object[] row : rows) {
            Long repoId     = ((Number) row[0]).longValue();
            Instant created = (Instant) row[1];
            Instant closed  = (Instant) row[2];
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
        for (Object[] row : prReviewRepository.findFirstReviewTimestampsByPrIds(prIds)) {
            firstReviewByPrId.put(((Number) row[0]).longValue(), (Instant) row[1]);
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
        List<Object[]> rows = new ArrayList<>(commitRepository
                .aggregateCommitsDailyByRepoIdsAndAuthorEmail(repoIds, user.getEmail(), from, to));

        Set<LocalDate> daysWithCommits = new HashSet<>();
        for (Object[] row : rows) {
            long count = ((Number) row[2]).longValue();
            if (count > 0) daysWithCommits.add(((java.sql.Date) row[0]).toLocalDate());
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
