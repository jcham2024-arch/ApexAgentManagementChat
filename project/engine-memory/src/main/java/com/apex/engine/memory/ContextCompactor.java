package com.apex.engine.memory;

import java.util.List;

/**
 * Compacts a conversation history to fit within a token budget while
 * preserving invariant C4: the SYSTEM message at index 0 is never
 * dropped, and any tool-call/result pair is preserved or dropped as a
 * unit, never split.
 *
 * <p>The history is a {@code List<String>} where index 0 is the
 * system prompt, index 1 begins with {@code "TASK: "}, and indices 2+
 * alternate {@code "ASSISTANT: "} and {@code "TOOL_RESULT: "} turns.
 * Implementations must respect that convention.
 */
public interface ContextCompactor {
    List<String> compact(List<String> history, int maxTokens);
}
