package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * One observability span. {@code name} identifies the measured
 * operation; {@code durationMs} measures it.
 */
public record TelemetrySpan(String name, long durationMs) {
    public TelemetrySpan {
        Objects.requireNonNull(name, "name");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0");
        }
    }
}
