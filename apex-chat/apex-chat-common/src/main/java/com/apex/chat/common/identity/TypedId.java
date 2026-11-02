package com.apex.chat.common.identity;

import java.util.Objects;

/**
 * A type-tagged identifier. Prevents raw {@code String} identity
 * throughout Layer 1.
 *
 * @param <T> the type this identifier refers to
 */
public record TypedId<T>(String value) {
    public TypedId {
        Objects.requireNonNull(value, "TypedId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TypedId must not be blank");
        }
    }
}
