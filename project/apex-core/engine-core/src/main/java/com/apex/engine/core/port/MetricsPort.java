package com.apex.engine.core.port;

import java.util.Objects;

/**
 * Application metrics counters and histograms. Tier 0 port; implementations
 * live in adapters (e.g. engine-metrics over Micrometer). The core never
 * imports an adapter. All metrics are additive and deterministic so the
 * same plan run produces the same metric sample.
 */
public interface MetricsPort {

    /** Increments a counter by {@code delta} (default 1). */
    void counter(String name, long delta);

    /** Records a single observation against a histogram. */
    void histogram(String name, double value);

    /** Returns the current counter value for {@code name}, or 0 if unset. */
    long counterValue(String name);
}
