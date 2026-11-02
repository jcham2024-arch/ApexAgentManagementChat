package com.apex.engine.core.port;

import com.apex.engine.core.concurrent.CancellationToken;
import com.apex.engine.core.port.model.ChatChunk;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;

import java.util.function.Consumer;

/**
 * Provider-agnostic chat inference. Tier 1 impl: engine-llm-http
 * (S2.4). Tier 2 impl: adapter-spring-ai (S3+). First impl lands in
 * S2.4. Streaming is added in S2.4 as an additive interface method.
 */
public interface ChatModelPort {
    ChatResponse chat(ChatRequest request);

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
}
