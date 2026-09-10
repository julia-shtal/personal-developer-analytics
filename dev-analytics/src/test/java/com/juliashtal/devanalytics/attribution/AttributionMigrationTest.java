package com.juliashtal.devanalytics.attribution;

import com.juliashtal.devanalytics.exception.ConflictException;
import com.juliashtal.devanalytics.git.model.GitRepositoryEntity;
import com.juliashtal.devanalytics.git.repository.GitRepositoryEntityRepository;
import com.juliashtal.devanalytics.github.service.GitHubIdentityBackfill;
import com.juliashtal.devanalytics.jira.service.JiraIdentityBackfill;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillTrigger;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import com.juliashtal.devanalytics.user.service.AuthorIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for the attribution migration.
 *
 * <p>Two properties carry the whole design. The {@code identity_backfilled_at} marker is the job's
 * only durable progress record, so it must be stamped strictly after all four sub-steps for a
 * repository succeed — stamping early would permanently skip a repository whose records were never
 * finished, and there is no second signal that would notice.
 *
 * <p>And the metric recompute is gated on nothing being left pending. Recomputing over a
 * half-migrated history produces numbers that look authoritative while missing exactly the records
 * the job has not reached, and the coverage ledger would then mark those days done.
 */
@ExtendWith(MockitoExtension.class)
class AttributionMigrationTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class JobSteps {

        @Mock UserRepository userRepository;
        @Mock GitRepositoryEntityRepository repoRepository;
        @Mock GitHubIdentityBackfill githubBackfill;
        @Mock JiraIdentityBackfill jiraBackfill;
        @Mock AuthorIdentityService authorIdentityService;
        @Mock MetricBackfillTrigger backfillTrigger;

        @InjectMocks AttributionMigrationJob job;

        private GitRepositoryEntity repo;

        @BeforeEach
        void setUp() {
            repo = new GitRepositoryEntity();
            repo.setId(10L);
            repo.setName("owner/legacy-repo");
            // Newly constructed entities self-stamp; a repo collected before attribution existed
            // is loaded from a row where the column is null, which is what the queue selects on.
            repo.setIdentityBackfilledAt(null);

            lenient().when(userRepository.findByGithubLoginIsNotNullAndGithubUserIdIsNull())
                    .thenReturn(List.of());
            lenient().when(userRepository.findAll()).thenReturn(List.of());
        }

        @Test
        void run_repositoryWhereEverySubStepSucceeds_stampsTheMarker() {
            when(repoRepository.findPendingIdentityBackfill()).thenReturn(List.of(repo));
            when(repoRepository.countByIdentityBackfilledAtIsNull()).thenReturn(0L);

            job.run(() -> {});

            assertThat(repo.getIdentityBackfilledAt()).isNotNull();
            verify(repoRepository).save(repo);
        }

        @Test
        void run_allFourSubStepsInOrder_areInvokedForThePendingRepository() {
            when(repoRepository.findPendingIdentityBackfill()).thenReturn(List.of(repo));
            when(repoRepository.countByIdentityBackfilledAtIsNull()).thenReturn(0L);

            job.run(() -> {});

            InOrder order = inOrder(githubBackfill);
            order.verify(githubBackfill).backfillCommits(repo);
            order.verify(githubBackfill).backfillPullRequests(repo);
            order.verify(githubBackfill).refreshReviews(repo);
            order.verify(githubBackfill).backfillIssues(repo);
        }

        @Test
        void run_reviewStepFails_leavesTheMarkerNullSoTheNextRunRetries() {
            when(repoRepository.findPendingIdentityBackfill()).thenReturn(List.of(repo));
            doThrow(new IllegalStateException("GitHub unreachable"))
                    .when(githubBackfill).refreshReviews(repo);
            when(repoRepository.countByIdentityBackfilledAtIsNull()).thenReturn(1L);

            job.run(() -> {});

            assertThat(repo.getIdentityBackfilledAt()).isNull();
            verify(repoRepository, never()).save(repo);
        }

        @Test
        void run_repositoryStillPending_skipsTheMetricRecompute() {
            when(repoRepository.findPendingIdentityBackfill()).thenReturn(List.of(repo));
            doThrow(new IllegalStateException("GitHub unreachable"))
                    .when(githubBackfill).refreshReviews(repo);
            when(repoRepository.countByIdentityBackfilledAtIsNull()).thenReturn(1L);

            job.run(() -> {});

            verify(backfillTrigger, never()).onAttributionChanged(anyLong());
        }

        @Test
        void run_nothingPendingAfterTheRepositoryPass_recomputesEveryUser() {
            User user = new User();
            user.setId(1L);
            when(repoRepository.findPendingIdentityBackfill()).thenReturn(List.of(repo));
            when(repoRepository.countByIdentityBackfilledAtIsNull()).thenReturn(0L);
            when(userRepository.findAll()).thenReturn(List.of(user));

            job.run(() -> {});

            verify(backfillTrigger).onAttributionChanged(1L);
        }

        @Test
        void run_secondRunWithNothingPending_touchesNoRepository() {
            // Idempotence at the run level: the marker set by the first run empties the queue.
            when(repoRepository.findPendingIdentityBackfill()).thenReturn(List.of());
            when(repoRepository.countByIdentityBackfilledAtIsNull()).thenReturn(0L);

            job.run(() -> {});

            verifyNoInteractions(githubBackfill);
        }

        @Test
        void run_userLoginNoLongerResolvable_continuesWithTheRemainingUsers() {
            User missing = new User();
            missing.setId(1L);
            missing.setGithubLogin("ghost");
            User ok = new User();
            ok.setId(2L);
            ok.setGithubLogin("octocat");

            when(userRepository.findByGithubLoginIsNotNullAndGithubUserIdIsNull())
                    .thenReturn(List.of(missing, ok));
            doThrow(new IllegalStateException("not found"))
                    .when(authorIdentityService).setGithubIdentity(1L, "ghost");
            when(repoRepository.findPendingIdentityBackfill()).thenReturn(List.of());
            when(repoRepository.countByIdentityBackfilledAtIsNull()).thenReturn(0L);

            job.run(() -> {});

            verify(authorIdentityService).setGithubIdentity(2L, "octocat");
        }

        @Test
        void run_stepThrows_stillReleasesTheInProgressFlag() {
            // Without the finally, one failure would leave the migration un-startable forever.
            when(repoRepository.findPendingIdentityBackfill())
                    .thenThrow(new IllegalStateException("database down"));
            boolean[] released = {false};

            job.run(() -> released[0] = true);

            assertThat(released[0]).isTrue();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class StartGuard {

        @Mock UserRepository userRepository;
        @Mock GitRepositoryEntityRepository repoRepository;
        @Mock AttributionMigrationJob job;

        @Test
        void start_noRunInProgress_startsTheJob() {
            AttributionMigrationService service = service(new MockEnvironment());

            service.start();

            verify(job).run(any());
        }

        @Test
        void start_runAlreadyInProgress_throwsConflict() {
            // The stub never invokes the callback, so the flag stays set -- exactly the state a
            // long-running migration is in when a second request arrives.
            AttributionMigrationService service = service(new MockEnvironment());
            service.start();

            assertThatThrownBy(service::start).isInstanceOf(ConflictException.class);
        }

        @Test
        void start_afterTheJobFinishes_canBeStartedAgain() {
            doAnswer(inv -> {
                inv.getArgument(0, Runnable.class).run();
                return null;
            }).when(job).run(any());
            AttributionMigrationService service = service(new MockEnvironment());

            service.start();

            assertThat(service.status().running()).isFalse();
            service.start();
            verify(job, times(2)).run(any());
        }

        @Test
        void start_demoProfileActive_throwsConflict() {
            // Demo snapshots are seeded with no records behind them: a recompute would delete the
            // demo data and rebuild nothing.
            MockEnvironment demo = new MockEnvironment();
            demo.setActiveProfiles("demo");

            assertThatThrownBy(() -> service(demo).start()).isInstanceOf(ConflictException.class);
            verifyNoInteractions(job);
        }

        @Test
        void status_reportsPendingAndDoneCountsAndIdleState() {
            when(userRepository.countByGithubLoginIsNotNullAndGithubUserIdIsNull()).thenReturn(2L);
            when(repoRepository.countByIdentityBackfilledAtIsNull()).thenReturn(3L);
            when(repoRepository.countByIdentityBackfilledAtIsNotNull()).thenReturn(4L);

            AttributionMigrationStatus status = service(new MockEnvironment()).status();

            assertThat(status.usersWithoutGithubId()).isEqualTo(2L);
            assertThat(status.pendingRepos()).isEqualTo(3L);
            assertThat(status.doneRepos()).isEqualTo(4L);
            assertThat(status.running()).isFalse();
        }

        private AttributionMigrationService service(MockEnvironment environment) {
            return new AttributionMigrationService(userRepository, repoRepository, job, environment);
        }
    }
}
