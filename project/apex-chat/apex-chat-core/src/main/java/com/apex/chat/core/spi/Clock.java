package com.apex.chat.core.spi;

import java.time.Instant;

/**
 * Injectable clock for deterministic tests. Implementations may return
 * a fixed instant, a mocked clock, or {@link java.time.Clock#systemUTC()}.
 */
public interface Clock {

    /**
     * Returns the current instant.
     */
    Instant now();

    /**
     * Returns the current instant in milliseconds since the epoch.
     */
    default long nowMillis() {
        return now().toEpochMilli();
    }
}
