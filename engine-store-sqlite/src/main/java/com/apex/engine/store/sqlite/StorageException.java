package com.apex.engine.store.sqlite;

/**
 * Failure from the SQLite storage adapter. {@link #category()}
 * classifies the failure: {@code init}, {@code append}, {@code query},
 * {@code duplicate}.
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
