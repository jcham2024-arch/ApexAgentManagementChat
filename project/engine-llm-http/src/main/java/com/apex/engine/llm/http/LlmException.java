package com.apex.engine.llm.http;

/**
 * Runtime failure from a chat model adapter. The {@link #category()}
 * classifies the failure for logging and retry logic in later phases.
 *
 * <p>Categories: {@code config}, {@code network}, {@code timeout},
 * {@code auth}, {@code rate_limit}, {@code client}, {@code server},
 * {@code parse}, {@code stream_io}, {@code cancelled}.
 */
public final class LlmException extends RuntimeException {
    private final String category;

    public LlmException(String category, String message) {
        super(message);
        this.category = category;
    }

    public LlmException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
