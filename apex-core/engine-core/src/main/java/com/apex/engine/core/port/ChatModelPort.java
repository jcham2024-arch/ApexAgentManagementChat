package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;

/**
 * Provider-agnostic chat inference. Tier 1 impl: engine-llm-http
 * (S2.4). Tier 2 impl: adapter-spring-ai (S3+). First impl lands in
 * S2.4. Streaming is added in S2.4 as an additive interface method.
 */
public interface ChatModelPort {
    ChatResponse chat(ChatRequest request);
}
