package com.apex.engine.planner.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation. {@link #cancel()} is idempotent and fires
 * exactly once. Long-running operations must check
 * {@link #isCancelled()} at loop boundaries and honour the token within
 * 100ms of cancellation (K6).
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
