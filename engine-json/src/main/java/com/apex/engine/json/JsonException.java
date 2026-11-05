package com.apex.engine.json;

/**
 * Thrown by {@link Json} on malformed input. Adapters catch this and
 * re-wrap as their own exception type ({@code ToolException} in
 * engine-tools-impl, {@code LlmException} in engine-llm-http).
 */
public final class JsonException extends RuntimeException {
    public JsonException(String message) {
        super(message);
    }
}
