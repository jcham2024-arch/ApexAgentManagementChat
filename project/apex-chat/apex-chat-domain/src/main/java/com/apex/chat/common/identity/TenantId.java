package com.apex.chat.common.identity;


/**
 * Identifies a tenant. S1.1 declared this type but never added it, so
 * S3.1 creates it here: {@code Principal} carries it and S3.2 scopes
 * storage queries by it. Validation matches {@link TypedId}.
 */
public record TenantId(String value) {
    public TenantId {
        value = IdValidation.requireNonBlank(value, "TenantId value");
    }
}
