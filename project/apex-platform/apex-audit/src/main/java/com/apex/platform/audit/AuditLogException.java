package com.apex.platform.audit;

/**
 * Failure from the audit log. Categories: {@code config},
 * {@code tamper}. Never thrown on a normal append — only on
 * construction (bad secret) or verification (broken chain).
 */
public final class AuditLogException extends RuntimeException {
    private final String category;

    public AuditLogException(String category, String message) {
        super(message);
        this.category = category;
    }

    public AuditLogException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
