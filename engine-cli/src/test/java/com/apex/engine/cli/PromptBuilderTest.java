package com.apex.engine.cli;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder prompts = new PromptBuilder();

    private static TaskDefinition task() {
        return new TaskDefinition("t", "d", "Do the thing", 3, "m",
                List.of("read_file", "write_file"), List.of(), List.of(),
                List.of());
    }

    @Test
    void initialPartsNameEveryTool() {
        List<String> parts = prompts.initialParts(task());

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).contains("read_file").contains("write_file");
    }

    @Test
    void initialPartsIncludeInstructionsVerbatim() {
        assertThat(prompts.initialParts(task()).get(1))
                .isEqualTo("TASK: Do the thing");
    }

    @Test
    void withDecisionAppendsAssistantLine() {
        List<String> history = prompts.initialParts(task());

        List<String> next = prompts.withDecision(history, "{\"done\":true}");

        assertThat(next).hasSize(history.size() + 1);
        assertThat(next.get(next.size() - 1))
                .isEqualTo("ASSISTANT: {\"done\":true}");
    }

    @Test
    void withToolResultAppendsToolResultLine() {
        List<String> history = prompts.initialParts(task());

        List<String> next = prompts.withToolResult(history, "file contents");

        assertThat(next.get(next.size() - 1))
                .isEqualTo("TOOL_RESULT: file contents");
    }

    @Test
    void originalHistoryIsNeverMutated() {
        List<String> history = new ArrayList<>(prompts.initialParts(task()));

        prompts.withDecision(history, "a");
        prompts.withToolResult(history, "b");

        assertThat(history).hasSize(2);
    }
}
