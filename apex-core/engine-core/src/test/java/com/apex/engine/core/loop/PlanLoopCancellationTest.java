package com.apex.engine.core.loop;

import com.apex.engine.planner.concurrent.CancellationToken;
import com.apex.engine.core.port.ChatEventSink;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.apex.engine.core.loop.LoopActions.run;
import static com.apex.engine.core.loop.LoopActions.slow;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * K6: cooperative cancellation is observable within 100ms. The slow
 * action polls the predicate the loop hands it and yields, then the
 * loop's own boundary check reports CANCELLED.
 */
class PlanLoopCancellationTest {

    @Test
    void cancellationIsHonouredWithinOneHundredMilliseconds() {
        CancellationToken token = new CancellationToken();
        AtomicReference<Instant> cancelledAt = new AtomicReference<>();
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            cancelledAt.set(Instant.now());
            token.cancel();
        });
        canceller.start();

        PlanLoopResult result = run(5, token, List.of(slow()), ChatEventSink.NOOP);
        Instant returned = Instant.now();

        try {
            canceller.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(result.status()).isEqualTo(PlanStatus.CANCELLED);
        assertThat(cancelledAt.get()).isNotNull();
        assertThat(Duration.between(cancelledAt.get(), returned).toMillis())
                .isLessThan(100L);
    }

    @Test
    void cancelIsIdempotent() {
        CancellationToken token = new CancellationToken();

        assertThat(token.cancel()).isTrue();
        assertThat(token.cancel()).isFalse();
        assertThat(token.isCancelled()).isTrue();
    }
}
