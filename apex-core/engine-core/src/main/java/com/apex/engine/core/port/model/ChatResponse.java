package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * A single chat completion. {@code content} is the model's text output.
 */
public record ChatResponse(String content) {
    public ChatResponse {
        Objects.requireNonNull(content, "content");
    }
}
