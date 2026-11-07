package com.apex.engine.core.port.model;

import java.util.Map;
import java.util.Objects;

/**
 * Result of an evaluation probe evaluation. Tier 0.
 */
public record EvaluationProbeResult(
        String probeName,
        double score,
        boolean passed,
        String feedback,
        Map<String, Object> metadata
) {
    public EvaluationProbeResult {
        Objects.requireNonNull(probeName, "probeName");
        Objects.requireNonNull(feedback, "feedback");
        Objects.requireNonNull(metadata, "metadata");
        probeName = probeName.trim();
        if (probeName.isBlank()) {
            throw new IllegalArgumentException("probeName must not be blank");
        }
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be between 0 and 1");
        }
        metadata = Map.copyOf(metadata);
    }
}
