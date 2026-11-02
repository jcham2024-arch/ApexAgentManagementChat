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
