package com.apex.chat.core;

import com.apex.chat.common.identity.IdValidation;

/**
 * Immutable identifier for a chat session. Prevents raw {@code String}
 * identity leaking across module boundaries.
 */
public record ChatSessionId(String value) {
    public ChatSessionId {
        value = IdValidation.requireNonBlank(value, "ChatSessionId value");
    }
}
