# PHASE-S2.4-MODEL-ADAPTER-CORE-PORT.md — Session Package, Part 2 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 2 of 8:** §4.1–§4.2, ChatChunk and the default stream method.
Parts: SESSION · CORE-PORT · LLM-BASICS · CONFIG · PROTOCOL · PROVIDER ·
METADATA-TESTS · CRITERIA, each `PHASE-S2.4-MODEL-ADAPTER-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 4. Files — `engine-core`

### 4.1 `engine-core/src/main/java/com/apex/engine/core/port/model/ChatChunk.java`

```java
package com.apex.engine.core.port.model;

import java.util.Objects;

/**
 * A single delta from a streaming chat response. {@code done} marks the
 * final chunk; {@code finishReason} is set on that chunk (typically
 * {@code "stop"} or {@code "length"}). Empty deltas with {@code done ==
 * true} are valid — they mark termination after the last content chunk.
 */
public record ChatChunk(String delta, boolean done, String finishReason) {
    public ChatChunk {
        Objects.requireNonNull(delta, "delta");
        // finishReason may be null while streaming; only require it on
        // the terminal chunk.
        if (done && finishReason == null) {
            throw new IllegalArgumentException(
                    "finishReason required when done=true");
        }
    }
}
```

### 4.2 `engine-core/.../port/ChatModelPort.java` — add method

Add these imports:

```java
import com.apex.engine.core.concurrent.CancellationToken;
import com.apex.engine.core.port.model.ChatChunk;
import java.util.function.Consumer;
```

Add inside the interface (after `chat`):

```java
/**
 * Streaming variant. The default implementation calls
 * {@link #chat(ChatRequest)} once and emits a single terminal chunk.
 * Override to stream real deltas.
 *
 * <p>Implementations must check {@code token} between chunks. If the
 * provider is slow to send the next chunk, cancellation is delayed
 * until that chunk arrives.
 */
default void stream(ChatRequest request, CancellationToken token,
                    Consumer<ChatChunk> onChunk) {
    ChatResponse response = chat(request);
    if (!token.isCancelled()) {
        onChunk.accept(new ChatChunk(response.content(), true, "stop"));
    }
}
```
