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
 * <p>{@link TransactionPhase#AFTER_COMMIT} because the recomputation reads the identity the
 * publishing transaction is still writing, and {@code @Async} because a full recompute walks the
 * user's whole history. Disabled under the {@code demo} profile, whose seeded snapshots have no
 * records behind them to rebuild from.</p>
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
            // Nothing above can react on a pool thread after commit; the nightly backfill
            // rebuilds what is missing, since the purge also cleared the coverage.
            log.error("Metric recomputation failed for userId={}", event.userId(), e);
        }
    }
}
