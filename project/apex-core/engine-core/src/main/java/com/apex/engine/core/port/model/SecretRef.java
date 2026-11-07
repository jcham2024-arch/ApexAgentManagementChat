package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * A reference to a stored secret by name. The value itself is never
 * carried by this record.
 */
public record SecretRef(String name) {
    public SecretRef {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("SecretRef name must not be blank");
        }
    }
}
