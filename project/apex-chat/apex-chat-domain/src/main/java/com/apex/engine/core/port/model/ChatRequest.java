package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Objects;

/**
 * A single chat request. Prompt parts are preassembled by the caller;
 * the port does not chunk, template, or fragment.
 */
public record ChatRequest(String modelId, List<String> promptParts) {
    public ChatRequest {
        Objects.requireNonNull(modelId, "modelId");
        promptParts = List.copyOf(promptParts);
    }
}
