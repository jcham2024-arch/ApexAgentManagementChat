package com.apex.chat.common.identity;

/**
 * A type-tagged identifier. Prevents raw {@code String} identity
 * throughout Layer 1.
 *
 * @param <T> the type this identifier refers to
 */
public record TypedId<T>(String value) {
    public TypedId {
        value = IdValidation.requireNonBlank(value, "TypedId value");
    }
}
