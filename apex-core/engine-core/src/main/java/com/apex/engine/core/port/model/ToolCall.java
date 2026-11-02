package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * A request to run one tool. Arguments are raw JSON; parsing is the
 * tool executor's job.
 */
public record ToolCall(String id, String toolName, String argumentsJson) {
    public ToolCall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(argumentsJson, "argumentsJson");
    }
}
