package com.juliashtal.devanalytics.demo;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.metrics.calc.MetricSnapshotWriter;
import com.juliashtal.devanalytics.metrics.model.MetricType;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Random;

/**
 * Seeds demo users, data sources, and metrics when the 'demo' profile is active.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private static final String DEMO_EMAIL     = "demo@demo.com";
    private static final String TEAMMATE_EMAIL = "teammate@demo.com";
    private static final String DEMO_PASSWORD  = "demo";
    private static final int    WEEKS          = 12;

    private final UserRepository                 userRepo;
    private final TeamRepository                 teamRepo;
    private final DataSourceConfigRepository     dsRepo;
    private final GitRepositoryEntityRepository  gitRepoRepo;
    private final UserRepoRegistrationRepository registrationRepo;
    private final MetricSnapshotWriter           metricSnapshotWriter;
    private final PasswordEncoder                encoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepo.findByEmail(DEMO_EMAIL).isPresent()) {
            log.info("Demo data already seeded -- skipping.");
            return;
        }

        User demo     = createUser(DEMO_EMAIL,     "demo-developer", "Europe/Warsaw", Role.MANAGER);
        User teammate = createUser(TEAMMATE_EMAIL, "demo-teammate",  "Europe/Warsaw", Role.DEVELOPER);
        Team team     = createTeam("Demo Team", demo, teammate);
        DataSourceConfig ds   = createDataSource(demo);
        GitRepositoryEntity repo = createRepo(ds);
        registerUser(demo,     repo);
        registerUser(teammate, repo);

        seedMetrics(demo, teammate, team, repo);

        log.info("Demo seeding complete. Login: {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private User createUser(String email, String username, String timezone, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setRole(role);
        u.setTimezone(timezone);
        u.setAvatarPreset("preset-01");
        return userRepo.save(u);
    }

    private Team createTeam(String name, User manager, User member) {
        Team t = new Team();
        t.setName(name);
        t.setManager(manager);
        t.getMembers().add(manager);
        t.getMembers().add(member);
        return teamRepo.save(t);
    }

    private DataSourceConfig createDataSource(User owner) {
        DataSourceConfig ds = new DataSourceConfig();
        ds.setUser(owner);
        ds.setType(DataSourceType.GIT_LOCAL);
        ds.setName("demo-source");
        ds.setPath("/demo/repo");
        return dsRepo.save(ds);
    }

    private GitRepositoryEntity createRepo(DataSourceConfig ds) {
        GitRepositoryEntity repo = new GitRepositoryEntity();
        repo.setDataSourceConfig(ds);
        repo.setRepoType(RepoType.LOCAL);
        repo.setName("demo-repo");
        repo.setRepoFullName("demo/demo-repo");
        repo.setLocalPath("/demo/repo");
        return gitRepoRepo.save(repo);
    }

    private void registerUser(User user, GitRepositoryEntity repo) {
        if (registrationRepo.existsByUserIdAndRepositoryId(user.getId(), repo.getId())) return;
        UserRepoRegistration reg = new UserRepoRegistration();
        reg.setUser(user);
        reg.setRepository(repo);
        registrationRepo.save(reg);
    }

    /**
     * Seeds {@link #WEEKS} whole ISO calendar weeks ending with the last complete week
     * before today.
     *
     * <p>The windows are anchored on an ISO Monday rather than on yesterday, so the seeded
     * aggregate rows carry the same grain {@code MetricsService} writes. A rolling window
     * anchored on yesterday would produce periods no ISO-week read resolves cleanly, and
     * V58 — which deletes non-week-aligned rows for the five {@code aggregatePeriod} types
     * — would delete them on the next upgrade of an already-seeded demo database, with no
     * way to regenerate them: the seeder skips entirely once the demo user exists.
     */
    private void seedMetrics(User user, User teammate, Team team, GitRepositoryEntity repo) {
        LocalDate lastCompleteWeekStart = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1);

        for (int w = 0; w < WEEKS; w++) {
            LocalDate weekStart = lastCompleteWeekStart.minusWeeks(WEEKS - 1 - w);
            LocalDate weekEnd   = weekStart.plusDays(6);

            seedUserDailyMetrics(user, repo, weekStart, w);
            seedTeammateDailyMetrics(teammate, repo, weekStart, w);
            seedUserAggregateMetrics(user, repo, weekStart, weekEnd, w);
            seedTeamMetrics(user, team, repo, weekStart, weekEnd, w);
        }
    }

    /**
     * Seeds 7 days of personal daily metrics for the demo user. Each day gets its own
     * {@link Random} seeded as {@code w * 7 + d} so re-running the seeder is deterministic.
     */
    private void seedUserDailyMetrics(User user, GitRepositoryEntity repo, LocalDate weekStart, int w) {
        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);
            Random r = rng(w * 7 + d);
            snap(user, null, repo, day, MetricType.DAILY_COMMITS_COUNT,    r.nextInt(9),                null, null);
            snap(user, null, repo, day, MetricType.DAILY_COMMITS_AVG_SIZE, 10 + r.nextInt(391),         null, null);
            snap(user, null, repo, day, MetricType.DAILY_PR_CREATED,       r.nextInt(4),                null, null);
            snap(user, null, repo, day, MetricType.DAILY_PR_MERGED,        r.nextInt(4),                null, null);
            snap(user, null, repo, day, MetricType.DAILY_ISSUES_CREATED,   r.nextInt(5),                null, null);
            snap(user, null, repo, day, MetricType.DAILY_ISSUES_CLOSED,    r.nextInt(5),                null, null);
            snap(user, null, repo, day, MetricType.DAILY_CHURN_RATIO,      r.nextDouble() * 0.5,        null, null);
            snap(user, null, repo, day, MetricType.FOCUS_RATIO_DAYS_TASKS, 0.3 + r.nextDouble() * 0.65, null, null);
        }
    }

    /**
     * Seeds 7 days of personal daily metrics for the teammate, populating the team member view.
     * The {@code 30_000} seed offset keeps its {@link Random} sequence independent of the demo user's.
     */
    private void seedTeammateDailyMetrics(User teammate, GitRepositoryEntity repo, LocalDate weekStart, int w) {
        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);
            Random rm = rng(30_000 + w * 7 + d);
            snap(teammate, null, repo, day, MetricType.DAILY_COMMITS_COUNT,    rm.nextInt(7),                null, null);
            snap(teammate, null, repo, day, MetricType.DAILY_COMMITS_AVG_SIZE, 10 + rm.nextInt(300),         null, null);
            snap(teammate, null, repo, day, MetricType.DAILY_PR_CREATED,       rm.nextInt(3),                null, null);
            snap(teammate, null, repo, day, MetricType.DAILY_PR_MERGED,        rm.nextInt(3),                null, null);
            snap(teammate, null, repo, day, MetricType.FOCUS_RATIO_DAYS_TASKS, 0.3 + rm.nextDouble() * 0.65, null, null);
        }
    }

    /**
     * Seeds one weekly-window snapshot per aggregate metric type for the demo user.
     * The {@code 10_000} seed offset keeps its {@link Random} sequence independent of the daily seeders.
     */
    private void seedUserAggregateMetrics(User user, GitRepositoryEntity repo, LocalDate weekStart, LocalDate weekEnd, int w) {
        Random ra = rng(10_000 + w);
        snap(user, null, repo, weekStart, MetricType.PR_LEAD_TIME_HOURS_MEDIAN,                       1 + ra.nextInt(72),           weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.ISSUE_LEAD_TIME_HOURS_MEDIAN,                    1 + ra.nextInt(168),          weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.PR_FIRST_COMMIT_TO_MERGE_LEAD_TIME_HOURS_MEDIAN, 2 + ra.nextInt(95),           weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.REVIEW_RESPONSE_TIME_HOURS_MEDIAN,               0.5 + ra.nextDouble() * 23.5, weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.AFTER_HOURS_COMMIT_RATIO,                        ra.nextDouble() * 0.4,        weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.DEEP_WORK_STREAK_DAYS,                           ra.nextInt(8),                weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.KNOWLEDGE_SILO_SCORE,                            ra.nextDouble(),              weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.REFACTOR_RATIO,                                  ra.nextDouble() * 0.4,        weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.PR_SIZE_COMPLEXITY_SCORE,                        1 + ra.nextInt(500),          weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.MERGE_WITHOUT_REVIEW_RATIO,                      ra.nextDouble() * 0.3,        weekStart, weekEnd);
        snap(user, null, repo, weekStart, MetricType.COMMITS_PER_WEEK_AVG,                            ra.nextInt(6),                weekStart, weekEnd);
    }

    /**
     * Seeds team-scoped metrics: one weekly PR lead-time aggregate plus 7 days of team commit
     * counts. The {@code 20_000} seed offset keeps its {@link Random} sequence independent of
     * the other seeders.
     */
    private void seedTeamMetrics(User user, Team team, GitRepositoryEntity repo, LocalDate weekStart, LocalDate weekEnd, int w) {
        Random rt = rng(20_000 + w);
        snap(user, team, null, weekStart, MetricType.PR_LEAD_TIME_HOURS_MEDIAN, 1 + rt.nextInt(72), weekStart, weekEnd);
        for (int d = 0; d < 7; d++) {
            snap(user, team, repo, weekStart.plusDays(d), MetricType.DAILY_COMMITS_COUNT, rt.nextInt(20), null, null);
        }
    }

    private void snap(User user, Team team, GitRepositoryEntity repo,
                      LocalDate date, MetricType type, double value,
                      LocalDate periodFrom, LocalDate periodTo) {
        metricSnapshotWriter.save(user, team, date, type, value, repo, periodFrom, periodTo);
    }

    private static Random rng(int seed) {
        return new Random(seed);
    }
}
