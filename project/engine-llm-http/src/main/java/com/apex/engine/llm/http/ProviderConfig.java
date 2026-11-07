package com.apex.engine.llm.http;

import java.time.Duration;
import java.util.Objects;

/**
 * Connection parameters for a chat provider. Populated by
 * {@link ProviderConfigLoader}. Timeout applies to both connect and
 * total request; the streaming case may run longer than the timeout
 * between chunks (see {@code ChatModelPort.stream}).
 */
public record ProviderConfig(
        String baseUrl,
        String apiKey,
        String defaultModel,
        Duration timeout
) {
    public ProviderConfig {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(defaultModel, "defaultModel");
        Objects.requireNonNull(timeout, "timeout");
        if (baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl blank");
        if (apiKey.isBlank()) throw new IllegalArgumentException("apiKey blank");
        if (defaultModel.isBlank()) throw new IllegalArgumentException("defaultModel blank");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        // Normalize trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }
}
