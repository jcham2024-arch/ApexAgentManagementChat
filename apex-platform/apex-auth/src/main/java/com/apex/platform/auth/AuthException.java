package com.apex.platform.auth;

/**
 * Authentication or authorisation failure. Categories: {@code missing},
 * {@code malformed}, {@code signature}, {@code expired}, {@code claim}.
 */
public final class AuthException extends RuntimeException {
    private final String category;

    public AuthException(String category, String message) {
        super(message);
        this.category = category;
    }

    public AuthException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
