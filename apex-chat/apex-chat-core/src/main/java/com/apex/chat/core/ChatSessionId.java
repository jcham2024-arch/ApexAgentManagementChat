package com.apex.chat.core;

/**
 * Immutable identifier for a chat session. Prevents raw {@code String}
 * identity leaking across module boundaries.
 */
public record ChatSessionId(String value) {
    public ChatSessionId {
        java.util.Objects.requireNonNull(value, "ChatSessionId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ChatSessionId must not be blank");
        }
    }
}
