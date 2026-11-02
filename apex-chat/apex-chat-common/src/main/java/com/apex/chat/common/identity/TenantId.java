package com.apex.chat.common.identity;

import java.util.Objects;

/**
 * Identifies a tenant. S1.1 declared this type but never added it, so
 * S3.1 creates it here: {@code Principal} carries it and S3.2 scopes
 * storage queries by it. Validation matches {@link TypedId}.
 */
public record TenantId(String value) {
    public TenantId {
        Objects.requireNonNull(value, "TenantId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TenantId must not be blank");
        }
    }
}
