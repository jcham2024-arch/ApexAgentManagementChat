package com.apex.engine.metrics;

import com.apex.engine.core.port.MetricsPort;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;

/**
 * Pure-JDK implementation of {@link MetricsPort}. Counters are additive
 * {@link AtomicLong}s; histograms track count, sum, min, max per name so
 * consumers can compute percentiles downstream. No third-party
 * dependencies: keeps the enforcer ban on {@code io.micrometer*} effective
 * for Tier 0 while this Tier 2 module stays dependency-light.
 */
public final class MetricsAdapter implements MetricsPort {

    private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Histogram> histograms = new ConcurrentHashMap<>();

    private MetricsAdapter() {}

    public static MetricsAdapter create() {
        return new MetricsAdapter();
    }

    @Override
    public void counter(String name, long delta) {
        Objects.requireNonNull(name, "name");
        counters.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }

    @Override
    public void histogram(String name, double value) {
        Objects.requireNonNull(name, "name");
        histograms.computeIfAbsent(name, k -> new Histogram()).record(value);
    }

    @Override
    public long counterValue(String name) {
        Objects.requireNonNull(name, "name");
        AtomicLong counter = counters.get(name);
        return counter == null ? 0L : counter.get();
    }

    /** Snapshot of all counters (name -> value). */
    public ConcurrentMap<String, Long> counterSnapshot() {
        ConcurrentMap<String, Long> snapshot = new ConcurrentHashMap<>();
        counters.forEach((name, counter) -> snapshot.put(name, counter.get()));
        return snapshot;
    }

    /** Snapshot of all histograms (name -> {@link HistogramSnapshot}). */
    public ConcurrentMap<String, HistogramSnapshot> histogramSnapshot() {
        ConcurrentMap<String, HistogramSnapshot> snapshot = new ConcurrentHashMap<>();
        histograms.forEach((name, histogram) -> snapshot.put(name, histogram.snapshot()));
        return snapshot;
    }

    /** Accumulating histogram statistics for a single metric name. */
    public static final class Histogram {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong sum = new AtomicLong();
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);

        void record(double value) {
            long longValue = (long) value;
            count.incrementAndGet();
            sum.addAndGet(longValue);
            min.updateAndGet(current -> Math.min(current, longValue));
            max.updateAndGet(current -> Math.max(current, longValue));
        }

        HistogramSnapshot snapshot() {
            return new HistogramSnapshot(count.get(), sum.get(), min.get(), max.get());
        }
    }

    /** Immutable histogram snapshot. */
    public record HistogramSnapshot(long count, long sum, long min, long max) {
        public double average() {
            return count == 0 ? 0.0 : (double) sum / count;
        }
    }
}
