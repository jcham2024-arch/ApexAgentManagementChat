package com.apex.engine.otel;

import com.apex.engine.core.port.MetricsPort;
import com.apex.engine.core.port.TelemetryPort;
import com.apex.engine.core.port.model.TelemetrySpan;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;

/**
 * OpenTelemetry adapter for {@link TelemetryPort} and {@link MetricsPort}.
 * Tier 2. Emits spans to a configured OpenTelemetry tracer (falls back to a
 * no-op SDK when no exporter is present) and aggregates metrics in-memory.
 * Safe to construct in any environment.
 */
public final class OtelAdapter implements TelemetryPort, MetricsPort {

    private final Tracer tracer;
    private final ConcurrentMap<String, AtomicLong> counters =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DoubleSummary> histograms =
            new ConcurrentHashMap<>();

    public OtelAdapter() {
        this(GlobalOpenTelemetry.get());
    }

    OtelAdapter(io.opentelemetry.api.OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("apex.engine");
    }

    @Override
    public void record(TelemetrySpan span) {
        var current = tracer.spanBuilder(span.name())
                .setAttribute("apex.step.duration.ms", span.durationMs())
                .startSpan();
        current.end();
    }

    @Override
    public void counter(String name, long delta) {
        Objects.requireNonNull(name, "name");
        counters.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }

    @Override
    public void histogram(String name, double value) {
        Objects.requireNonNull(name, "name");
        histograms.computeIfAbsent(name, k -> new DoubleSummary()).record(value);
    }

    @Override
    public long counterValue(String name) {
        Objects.requireNonNull(name, "name");
        AtomicLong counter = counters.get(name);
        return counter == null ? 0L : counter.get();
    }

    /** Running summary for a single histogram metric name. */
    private static final class DoubleSummary {
        private long count;
        private double sum;
        private double min = Double.MAX_VALUE;
        private double max = Double.MIN_VALUE;

        synchronized void record(double value) {
            count++;
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        double average() {
            return count == 0 ? 0.0 : sum / count;
        }
    }
}
