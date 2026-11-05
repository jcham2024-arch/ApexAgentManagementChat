package com.apex.chat.core.spi;

/**
 * Injectable ID generator for deterministic tests. Implementations may
 * return UUIDs, sequential IDs, or any other scheme.
 */
public interface IdGenerator {

    /**
     * Generates a new identifier string.
     */
    String nextId();
}
