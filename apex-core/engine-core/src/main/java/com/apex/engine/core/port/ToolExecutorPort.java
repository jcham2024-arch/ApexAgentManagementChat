package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;

/**
 * Executes a tool call and returns its result. Tier 1 impl:
 * engine-tools-impl (S2.3). Tier 2 impl: none — Tier 1 only. First
 * impl lands in S2.3.
 */
public interface ToolExecutorPort {
    ToolResult execute(ToolCall call);
}
