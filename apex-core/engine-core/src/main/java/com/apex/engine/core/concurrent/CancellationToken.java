package com.apex.engine.core.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation. {@link #cancel()} is idempotent and fires
 * exactly once. Long-running operations must check
 * {@link #isCancelled()} at loop boundaries and honour the token within
 * 100ms of cancellation (K6).
 *
 * <p><b>Known flaw in the S2.2 phase file, temporary placement.</b> This
 * type cannot be named where it is most needed: engine-planner's
 * {@code Action} lives in a module this one depends on, so {@code
 * PlanLoop} hands the action {@code token::isCancelled} (a
 * {@link java.util.function.BooleanSupplier}) instead. Fix in the next
 * step: move this class into engine-planner and restore the token
 * parameter on {@code Action#execute}.
 */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CompletableFuture<Void> cancelFuture = new CompletableFuture<>();

    public boolean cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelFuture.complete(null);
            return true;
        }
        return false;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public CompletableFuture<Void> onCancel() {
        return cancelFuture;
    }
}
