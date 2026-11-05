package com.apex.engine.store;

/**
 * Failure from a storage adapter. {@link #category()} classifies the
 * failure: {@code init}, {@code append}, {@code query}, {@code duplicate}.
 *
 * <p>Tier 0: stored here (not in a Tier 1 adapter module) so that any
 * storage implementation can throw it without depending on a specific
 * adapter.
 */
public final class StorageException extends RuntimeException {
    private final String category;

    public StorageException(String category, String message) {
        super(message);
        this.category = category;
    }

    public StorageException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
