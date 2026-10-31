package com.apex.engine.tools.impl;

/**
 * Raised by a tool on failure. Carries a category (used in logs and
 * for later classification) and a human-readable message. The
 * executor converts it to a {@code ToolResult} with {@code isError =
 * true}.
 */
public final class ToolException extends RuntimeException {
    private final String category;

    public ToolException(String category, String message) {
        super(message);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
