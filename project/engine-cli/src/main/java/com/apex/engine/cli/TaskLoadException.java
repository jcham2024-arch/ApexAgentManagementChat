package com.apex.engine.cli;

/**
 * Raised when a task file cannot be read, parsed, or validated. The
 * message names the field or structural problem. Categories:
 * {@code io}, {@code yaml}, {@code schema}.
 */
public final class TaskLoadException extends RuntimeException {
    private final String category;

    public TaskLoadException(String category, String message) {
        super(message);
        this.category = category;
    }

    public TaskLoadException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
