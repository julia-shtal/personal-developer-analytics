package com.juliashtal.devanalytics.demo;

import com.juliashtal.devanalytics.config.SystemClock;
import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.model.RepoType;
import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.git.repository.UserRepoRegistrationRepository;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.github.repository.GitHubPrReviewRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.issue.model.IssueEntity;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserCommitEmail;
import com.juliashtal.devanalytics.user.repository.TeamRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.repository.UserCommitEmailRepository;
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
import java.util.List;
import java.util.Locale;

/**
 * Seeds demo users, a data source, and twelve weeks of synthetic activity when the 'demo' profile
 * is active, then runs the production calculators over it.
 *
 * <p>No metric snapshot is written directly: the demo has to exercise the pipeline it demonstrates,
 * so a metric that comes out empty is a calculator finding rather than a row to insert.</p>
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
    /** Fixed so the demo dataset is reproducible: the same seed yields the same twelve weeks. */
    private static final int    SEED           = 42;
    /** Synthetic GitHub account IDs. Far above any real account ID, so demo rows can never collide. */
    private static final long   DEMO_GITHUB_ID     = 900_000_001L;
    private static final long   TEAMMATE_GITHUB_ID = 900_000_002L;

    private final UserRepository                 userRepo;
    private final TeamRepository                 teamRepo;
    private final DataSourceConfigRepository     dsRepo;
    private final GitRepositoryEntityRepository  gitRepoRepo;
    private final UserRepoRegistrationRepository registrationRepo;
    private final UserCommitEmailRepository      commitEmailRepo;
    private final GitCommitEntityRepository      commitRepo;
    private final GitHubPullRequestRepository    prRepo;
    private final GitHubPrReviewRepository       reviewRepo;
    private final IssueRepository                issueRepo;
    private final MetricsService                 metricsService;
    private final PasswordEncoder                encoder;
    private final SystemClock                    systemClock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepo.findByEmail(DEMO_EMAIL).isPresent()) {
            log.info("Demo data already seeded -- skipping.");
            return;
        }

        User demo     = createUser(DEMO_EMAIL,     "demo-developer", "Europe/Warsaw", Role.MANAGER,
                                   DEMO_GITHUB_ID,     "demo-developer");
        User teammate = createUser(TEAMMATE_EMAIL, "demo-teammate",  "Europe/Warsaw", Role.DEVELOPER,
                                   TEAMMATE_GITHUB_ID, "demo-teammate");
        Team team     = createTeam("Demo Team", demo, teammate);
        DataSourceConfig ds   = createDataSource(demo);
        GitRepositoryEntity repo = createRepo(ds);
        registerUser(demo,     repo);
        registerUser(teammate, repo);

        LocalDate start = systemClock.today().with(DayOfWeek.MONDAY).minusWeeks(WEEKS);
        seedActivity(demo, teammate, repo, start);
        calculateFromActivity(demo, teammate, team, start);

        log.info("Demo seeding complete. Login: {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private User createUser(String email, String username, String timezone, Role role,
                            long githubUserId, String githubLogin) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setRole(role);
        u.setTimezone(timezone);
        u.setAvatarPreset("preset-01");
        u.setGithubLogin(githubLogin);
        u.setGithubUserId(githubUserId);
        User saved = userRepo.save(u);
        declareCommitEmail(saved, email);
        return saved;
    }

    /** Commits are matched on declared addresses; without one the commit calculators write nothing. */
    private void declareCommitEmail(User user, String email) {
        UserCommitEmail declared = new UserCommitEmail();
        declared.setUser(user);
        declared.setEmail(email.toLowerCase(Locale.ROOT));
        commitEmailRepo.save(declared);
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
     * Persists twelve weeks of synthetic activity for both demo accounts.
     *
     * <p>Every record is attributed by the identifiers the calculators match on, so the figures the
     * demo shows are computed from this history by the production pipeline rather than inserted.</p>
     */
    private void seedActivity(User demo, User teammate, GitRepositoryEntity repo, LocalDate start) {
        List<GitCommitEntity> demoCommits = DemoActivityGenerator.buildCommits(
                demo.getEmail(), demo.getUsername(), DEMO_GITHUB_ID, start, WEEKS, SEED);
        List<GitHubPullRequestEntity> prs = DemoActivityGenerator.buildPullRequests(
                DEMO_GITHUB_ID, demo.getUsername(), start, WEEKS, SEED);
        DemoActivityGenerator.linkCommitsToPullRequests(demoCommits, prs);

        persistCommits(demoCommits, repo);
        persistCommits(DemoActivityGenerator.buildCommits(
                teammate.getEmail(), teammate.getUsername(), TEAMMATE_GITHUB_ID, start, WEEKS, SEED + 1), repo);

        prs.forEach(pr -> pr.setRepository(repo));
        prRepo.saveAll(prs);
        reviewRepo.saveAll(DemoActivityGenerator.buildReviews(
                prs, TEAMMATE_GITHUB_ID, teammate.getUsername(), SEED));

        List<IssueEntity> issues = DemoActivityGenerator.buildIssues(
                DEMO_GITHUB_ID, demo.getUsername(), start, WEEKS, SEED);
        issues.forEach(i -> i.setRepository(repo));
        issueRepo.saveAll(issues);
    }

    private void persistCommits(List<GitCommitEntity> commits, GitRepositoryEntity repo) {
        commits.forEach(c -> c.setRepository(repo));
        commitRepo.saveAll(commits);
    }

    /**
     * Runs the production calculators over the seeded history.
     *
     * <p>The seeder writes no snapshot itself: a metric that stays empty here is a calculator
     * finding, not a row to insert by hand.</p>
     */
    private void calculateFromActivity(User demo, User teammate, Team team, LocalDate start) {
        LocalDate end = systemClock.yesterday();
        metricsService.calculateDailyMetrics(demo.getId(), start, end);
        metricsService.calculateDailyMetrics(teammate.getId(), start, end);
        metricsService.calculateForTeam(team.getId(), demo.getId(), start, end);
    }
}
