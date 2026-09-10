package com.juliashtal.devanalytics.metrics.service;

import com.juliashtal.devanalytics.user.model.AuthorIdentityChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recomputes a user's metrics after their attribution identity changed.
 *
 * <p>Lives in {@code metrics} rather than {@code user} so the identity service stays unaware of
 * what depends on it: it announces a change, and this package decides that the change invalidates
 * every stored metric.
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} because the recomputation reads the very identity the
 * publishing transaction is still writing. Firing earlier would recompute against the old
 * identity and, if that transaction then rolled back, against one that never existed.
 *
 * <p>{@code @Async} because a full recompute walks the user's entire history — far too slow for
 * the HTTP request that added an address. The response returns as soon as the change is stored;
 * metrics catch up behind it.
 *
 * <p>Disabled under the {@code demo} profile: seeded demo snapshots have no commits, PRs or
 * issues behind them, so recomputing would delete the demo data and rebuild nothing.
 */
@Component
@Profile("!demo")
@RequiredArgsConstructor
@Slf4j
public class AuthorIdentityChangedListener {

    private final MetricBackfillTrigger backfillTrigger;

    @Async("collectTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuthorIdentityChanged(AuthorIdentityChangedEvent event) {
        log.info("Attribution identity changed for userId={}, recomputing metrics", event.userId());
        try {
            backfillTrigger.onAttributionChanged(event.userId());
        } catch (RuntimeException e) {
            // Nothing above can react: the publishing transaction committed long ago and this
            // runs on a pool thread. Log and stop -- the nightly backfill rebuilds what is
            // missing, because the purge cleared the coverage that would otherwise skip it.
            log.error("Metric recomputation failed for userId={}", event.userId(), e);
        }
    }
}
