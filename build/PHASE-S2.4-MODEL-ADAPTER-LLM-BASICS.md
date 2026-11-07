# PHASE-S2.4-MODEL-ADAPTER-LLM-BASICS.md — Session Package, Part 3 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 3 of 8:** §5.1–§5.3, LlmException, JsonParser, ProviderConfig.
Parts: SESSION · CORE-PORT · LLM-BASICS · CONFIG · PROTOCOL · PROVIDER ·
METADATA-TESTS · CRITERIA, each `PHASE-S2.4-MODEL-ADAPTER-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 5. Files — `engine-llm-http`

Package `com.apex.engine.llm.http` for all files.

### 5.1 `LlmException.java`

```java
package com.apex.engine.llm.http;

/**
 * Runtime failure from a chat model adapter. The {@link #category()}
 * classifies the failure for logging and retry logic in later phases.
 *
 * <p>Categories: {@code config}, {@code network}, {@code timeout},
 * {@code auth}, {@code rate_limit}, {@code client}, {@code server},
 * {@code parse}, {@code stream_io}, {@code cancelled}.
 */
public final class LlmException extends RuntimeException {
    private final String category;

    public LlmException(String category, String message) {
        super(message);
        this.category = category;
    }

    public LlmException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
```

### 5.2 `JsonParser.java`

**Module-local duplicate of `engine-tools-impl/Json.java`. Do not
consolidate in this phase.** Throws `LlmException("json", ...)` for
malformed input.

Write the same parser structure as S2.3's `Json.java` with these
changes:

- Class name: `JsonParser` instead of `Json`
- Class-level Javadoc adds: *"Module-local parser. Sibling lives in
  `engine-tools-impl`. The duplication is intentional; consolidation
  tracked for a future refactor phase."*
- Every `throw new ToolException(...)` becomes
  `throw new LlmException("json", ...)`

Keep the public method `parseObject(String)`. Do not add other public
methods. The `parseArrayValue`, `parseString`, `parseNumber`,
`parseLiteral`, `skipWhitespace`, `peek`, `next`, `expect` helpers stay
private.

**Line target:** ~170 lines. If it exceeds 200, split the escape
handling into a nested `Escapes` helper class. Do not exceed 200.

### 5.3 `ProviderConfig.java`

```java
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
```
