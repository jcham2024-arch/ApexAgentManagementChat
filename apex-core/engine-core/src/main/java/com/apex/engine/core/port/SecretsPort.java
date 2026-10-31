package com.apex.engine.core.port;

import com.apex.engine.core.port.model.SecretRef;

/**
 * Credentials and API keys. Tier 1 impl: environment variables (S3).
 * Tier 2 impl: adapter-spring-vault (S3+). First impl lands in S3.
 */
public interface SecretsPort {
    String get(SecretRef ref);
}
