package com.apex.engine.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the conversation history sent to the model on each step.
 * Format is a sequence of prompt parts: initial system message, task
 * instructions, then alternating assistant/tool-result parts. Kept
 * under 200 lines; complex formatting goes into {@link #SYSTEM_TEMPLATE}.
 */
public final class PromptBuilder {

    private static final String SYSTEM_TEMPLATE = """
            You are an autonomous coding agent. You have these tools
            available: %s. On each turn you must reply with a single
            JSON object, no other text:
              To use a tool: {"action": "<tool>", "arguments": {...}}
              When finished: {"done": true, "summary": "<short summary>"}
            Do not include code fences. Do not explain. Only JSON.
            """;

    public List<String> initialParts(TaskDefinition task) {
        String system = SYSTEM_TEMPLATE.formatted(
                String.join(", ", task.tools()));
        return List.of(system, "TASK: " + task.instructions());
    }

    public List<String> withDecision(List<String> history, String rawResponse) {
        List<String> next = new ArrayList<>(history);
        next.add("ASSISTANT: " + rawResponse);
        return next;
    }

    public List<String> withToolResult(List<String> history, String output) {
        List<String> next = new ArrayList<>(history);
        next.add("TOOL_RESULT: " + output);
        return next;
    }
}
