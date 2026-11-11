package com.apex.engine.core.port;

/**
 * Thrown when a JWT token fails verification for any reason: bad
 * signature, expired, unknown key, disallowed algorithm, malformed,
 * missing required claims, etc.
 *
 * <p>Tier 0. Implementations of {@link TokenVerifier} throw this
 * exception (or a subclass) when a token cannot be verified. Callers
 * should not inspect the exception type to make authorization decisions;
 * any exception from {@link TokenVerifier#verify(String)} means the
 * token is not trusted.
 */
public class TokenVerificationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String category;

    public TokenVerificationException(String message) {
        super(message);
        this.category = "verification";
    }

    public TokenVerificationException(String message, Throwable cause) {
        super(message, cause);
        this.category = "verification";
    }

    public TokenVerificationException(String category, String message) {
        super(message);
        this.category = category;
    }

    public TokenVerificationException(String category, String message,
                                      Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
