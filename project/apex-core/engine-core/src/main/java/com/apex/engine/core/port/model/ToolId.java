package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * An opaque, typed identifier for a tool. Prevents raw {@code String}
 * tool identity leaking across module boundaries. Tier 0.
 */
public record ToolId(String value) {
    public ToolId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ToolId must not be blank");
        }
    }
}
