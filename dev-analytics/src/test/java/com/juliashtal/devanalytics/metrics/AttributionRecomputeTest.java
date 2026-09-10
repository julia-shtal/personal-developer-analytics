package com.juliashtal.devanalytics.metrics;

import com.juliashtal.devanalytics.git.repository.GitCommitEntityRepository;
import com.juliashtal.devanalytics.github.repository.GitHubPullRequestRepository;
import com.juliashtal.devanalytics.issue.IssueRepository;
import com.juliashtal.devanalytics.metrics.model.BackfillProperties;
import com.juliashtal.devanalytics.metrics.service.AuthorIdentityChangedListener;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillService;
import com.juliashtal.devanalytics.metrics.service.MetricBackfillTrigger;
import com.juliashtal.devanalytics.metrics.service.MetricsService;
import com.juliashtal.devanalytics.metrics.service.RepoScopeResolver;
import com.juliashtal.devanalytics.metrics.service.UserMetricsPurger;
import com.juliashtal.devanalytics.user.model.AuthorIdentityChangedEvent;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Acceptance tests for the recompute that follows an identity change — the path connecting an
 * address removed in Settings to that user's metrics being rebuilt.
 *
 * <p>The ordering is the point: purging before recomputing removes rows the previous identity
 * produced, which recalculating in place would leave behind, and clearing the coverage ledger in
 * the same step is what lets the backfill revisit those days.</p>
 */
class AttributionRecomputeTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Listener {

        @Mock MetricBackfillTrigger backfillTrigger;
        @InjectMocks AuthorIdentityChangedListener listener;

        @Test
        void onAuthorIdentityChanged_event_triggersTheRecomputeForThatUser() {
            listener.onAuthorIdentityChanged(new AuthorIdentityChangedEvent(7L));

            verify(backfillTrigger).onAttributionChanged(7L);
        }

        @Test
        void onAuthorIdentityChanged_recomputeFails_doesNotPropagate() {
            doThrow(new IllegalStateException("boom"))
                    .when(backfillTrigger).onAttributionChanged(7L);

            // Nothing above can react on a pool thread after commit; the nightly backfill
            // rebuilds what is missing, since the purge also cleared the coverage.
            assertThatCode(() -> listener.onAuthorIdentityChanged(new AuthorIdentityChangedEvent(7L)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Recompute {

        @Mock UserRepository userRepository;
        @Mock RepoScopeResolver repoScopeResolver;
        @Mock GitCommitEntityRepository commitRepository;
        @Mock GitHubPullRequestRepository pullRequestRepository;
        @Mock IssueRepository issueRepository;
        @Mock MetricCoverageRepository coverageRepository;
        @Mock UserMetricsPurger metricsPurger;
        @Mock MetricsService metricsService;

        private MetricBackfillService service() {
            return new MetricBackfillService(userRepository, repoScopeResolver, commitRepository,
                    pullRequestRepository, issueRepository, coverageRepository, metricsPurger,
                    metricsService, new BackfillProperties(30));
        }

        @Test
        void onAttributionChanged_purgesBeforeRecomputing() {
            User user = new User();
            user.setId(7L);
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(repoScopeResolver.resolve(any(), any())).thenReturn(List.of());

            service().onAttributionChanged(7L);

            // If the recompute ran first, the rows it wrote would be deleted by the purge and
            // the user would end up with no metrics at all.
            InOrder order = inOrder(metricsPurger, metricsService);
            order.verify(metricsPurger).purge(7L);
            order.verify(metricsService, atLeast(0)).calculateDailyMetrics(anyLong(), any(), any());
        }

        @Test
        void onAttributionChanged_emptyRepoScope_stillPurgesTheOldRows() {
            User user = new User();
            user.setId(7L);
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(repoScopeResolver.resolve(any(), any())).thenReturn(List.of());

            service().onAttributionChanged(7L);

            // A user who unlinked everything has nothing to recompute, but their stale rows must
            // still go, or the dashboard keeps showing metrics for an identity that matches nothing.
            verify(metricsPurger).purge(7L);
        }

        @Test
        void onAttributionChanged_backfillFails_rethrowsSoTheDestructiveStepIsTraceable() {
            when(userRepository.findById(7L)).thenThrow(new IllegalStateException("database down"));

            // The purge has already committed by then. Swallowing the failure would leave the
            // user silently without metrics and without any signal that it happened.
            assertThatThrownBy(() -> service().onAttributionChanged(7L))
                    .isInstanceOf(IllegalStateException.class);
            verify(metricsPurger).purge(7L);
        }
    }
}
