package com.apex.chat.core.telemetry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Objects;

/**
 * Token usage and latency measurements for a single LLM generation.
 * All fields are non-negative; {@code reasoningTokens} may be zero for
 * models that do not support reasoning.
 */
public record GenerationTelemetry(
    int promptTokens,
    int completionTokens,
    int reasoningTokens,
    Duration latency,
    Duration timeToFirstToken,
    String modelId
) {
    public GenerationTelemetry {
        Objects.requireNonNull(latency, "latency");
        Objects.requireNonNull(timeToFirstToken, "timeToFirstToken");
        Objects.requireNonNull(modelId, "modelId");
        if (promptTokens < 0) throw new IllegalArgumentException("promptTokens must be >= 0");
        if (completionTokens < 0) throw new IllegalArgumentException("completionTokens must be >= 0");
        if (reasoningTokens < 0) throw new IllegalArgumentException("reasoningTokens must be >= 0");
        if (latency.isNegative()) throw new IllegalArgumentException("latency must not be negative");
        if (timeToFirstToken.isNegative()) throw new IllegalArgumentException("timeToFirstToken must not be negative");
        if (modelId.isBlank()) throw new IllegalArgumentException("modelId must not be blank");
    }

    /**
     * Total token count across prompt, completion, and reasoning.
     */
    public int totalTokens() {
        return promptTokens + completionTokens + reasoningTokens;
    }
}
