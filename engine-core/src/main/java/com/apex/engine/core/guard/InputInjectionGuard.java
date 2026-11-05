@NullMarked
package com.apex.engine.core.guard;

/**
 * Policy gate for OS input injection (keystrokes, clicks, text entry).
 * Any future injection layer must call {@link #canInject(boolean)}
 * before synthesizing an input event (K7).
 *
 * <p>Three mechanisms:
 * <ul>
 *   <li><b>Dead-man switch</b>: disabled by default. Operator must
 *       construct with {@code enabled = true}.</li>
 *   <li><b>Rate limit</b>: at most {@code maxPerSecond} injections per
 *       rolling second. Further attempts within the window are rejected.</li>
 *   <li><b>Password block</b>: if {@code blockPasswordFields} is true
 *       (default), any injection targeting a password field is rejected
 *       regardless of rate.</li>
 * </ul>
 *
 * <p>Not thread-safe. Callers must serialize access. Injection is not
 * a high-throughput path.
 */
public final class InputInjectionGuard {

    private static final int DEFAULT_RATE = 5;
    private static final long WINDOW_MILLIS = 1000L;

    private final boolean enabled;
    private final int maxPerSecond;
    private final boolean blockPasswordFields;
    private final Deque<Instant> recent;

    public InputInjectionGuard() {
        this(false, DEFAULT_RATE, true);
    }

    public InputInjectionGuard(boolean enabled, int maxPerSecond,
                               boolean blockPasswordFields) {
        if (maxPerSecond <= 0) {
            throw new IllegalArgumentException("maxPerSecond must be positive");
        }
        this.enabled = enabled;
        this.maxPerSecond = maxPerSecond;
        this.blockPasswordFields = blockPasswordFields;
        this.recent = new ArrayDeque<>();
    }

    /**
     * Returns true if an injection targeting the given context may
     * proceed. If the return value is true, the injection has been
     * recorded against the rate window. If false, nothing is recorded.
     *
     * @param isPasswordField true if the injection targets a password
     *                        input; blockPasswordFields applies
     */
    public boolean canInject(boolean isPasswordField) {
        if (!enabled) return false;
        if (blockPasswordFields && isPasswordField) return false;
        Instant now = Instant.now();
        prune(now);
        if (recent.size() >= maxPerSecond) return false;
        recent.addLast(now);
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void prune(Instant now) {
        long cutoff = now.toEpochMilli() - WINDOW_MILLIS;
        while (!recent.isEmpty()
                && recent.peekFirst().toEpochMilli() < cutoff) {
            recent.removeFirst();
        }
    }
}
