package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * The outcome of one tool call. {@code output} carries the tool's text
 * or error text; {@code durationMs} measures the call.
 */
public record ToolResult(String callId, boolean isError, String output,
                         long durationMs) {
    public ToolResult {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(output, "output");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0");
        }
    }
}
