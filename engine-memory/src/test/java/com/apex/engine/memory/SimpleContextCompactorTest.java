package com.apex.engine.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleContextCompactorTest {

    private static String sys(String s) {
        return s;
    }

    private static String task(String s) {
        return "TASK: " + s;
    }

    private static String assistant(String s) {
        return "ASSISTANT: " + s;
    }

    private static String toolResult(String s) {
        return "TOOL_RESULT: " + s;
    }

    @Test
    void historyWithinBudgetReturnedAsIs() {
        List<String> history = List.of(sys("system"), task("do it"), assistant("ok"));
        SimpleContextCompactor compactor = new SimpleContextCompactor();
        List<String> result = compactor.compact(history, 1000);

        assertThat(result).containsExactlyElementsOf(history);
    }

    @Test
    void systemNeverDropped() {
        List<String> history = List.of(
                sys("SYSTEM_PROMPT_VERY_LONG______________________________"),
                task("old task"),
                assistant("old response"),
                task("new task"),
                assistant("new response"));
        SimpleContextCompactor compactor = new SimpleContextCompactor();
        List<String> result = compactor.compact(history, 50);

        assertThat(result.get(0)).isEqualTo("SYSTEM_PROMPT_VERY_LONG______________________________");
        assertThat(result).anyMatch(s -> s.contains("new task"));
        assertThat(result).anyMatch(s -> s.contains("new response"));
    }

    @Test
    void toolPairsKeptTogether() {
        List<String> history = List.of(
                sys("system"),
                assistant("call tool"),
                toolResult("result data"),
                assistant("final answer"));
        SimpleContextCompactor compactor = new SimpleContextCompactor();
        List<String> result = compactor.compact(history, 60);

        // Tool call + result must be adjacent in output
        assertThat(result).anyMatch(s -> s.contains("ASSISTANT: call tool"));
        assertThat(result).anyMatch(s -> s.contains("TOOL_RESULT: result data"));

        int callIdx = -1;
        int resultIdx = -1;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).contains("ASSISTANT: call tool")) callIdx = i;
            if (result.get(i).contains("TOOL_RESULT: result data")) resultIdx = i;
        }
        assertThat(callIdx).isNotEqualTo(-1);
        assertThat(resultIdx).isEqualTo(callIdx + 1);
    }

    @Test
    void olderEntriesDroppedFirst() {
        List<String> history = List.of(
                sys("system"),
                task("oldest"),
                assistant("old response 1"),
                task("old task 2"),
                assistant("old response 2"),
                task("recent"),
                assistant("recent response"));
        SimpleContextCompactor compactor = new SimpleContextCompactor();
        List<String> result = compactor.compact(history, 80);

        assertThat(result.get(0)).isEqualTo("system");
        assertThat(result).anyMatch(s -> s.contains("recent"));
        assertThat(result).anyMatch(s -> s.contains("recent response"));
        assertThat(result).noneMatch(s -> s.contains("oldest"));
        assertThat(result).noneMatch(s -> s.contains("old response 1"));
    }

    @Test
    void unmatchedAssistantEntryKeptAlone() {
        List<String> history = List.of(
                sys("system"),
                assistant("standalone response"));
        SimpleContextCompactor compactor = new SimpleContextCompactor();
        List<String> result = compactor.compact(history, 100);

        assertThat(result).containsExactly("system", "ASSISTANT: standalone response");
    }

    @Test
    void singleEntryHistory() {
        List<String> history = List.of(sys("only system"));
        SimpleContextCompactor compactor = new SimpleContextCompactor();
        List<String> result = compactor.compact(history, 10);

        assertThat(result).containsExactly("only system");
    }

    @Test
    void rejectsNullHistory() {
        SimpleContextCompactor compactor = new SimpleContextCompactor();
        assertThatThrownBy(() -> compactor.compact(null, 100))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNonPositiveMaxTokens() {
        SimpleContextCompactor compactor = new SimpleContextCompactor();
        assertThatThrownBy(() -> compactor.compact(List.of("x"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compactor.compact(List.of("x"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void c4SystemAlwaysPresentWhenAnythingKept() {
        List<String> history = List.of(
                sys("SYSTEM"),
                task("t1"), assistant("a1"),
                task("t2"), assistant("a2"),
                task("t3"), assistant("a3"));
        SimpleContextCompactor compactor = new SimpleContextCompactor();

        for (int budget = 1; budget <= 200; budget++) {
            List<String> result = compactor.compact(history, budget);
            assertThat(result).as("budget=%d", budget).isNotEmpty();
            assertThat(result.get(0)).as("budget=%d", budget).isEqualTo("SYSTEM");
        }
    }

    @Test
    void c4ToolPairNeverSplit() {
        List<String> history = List.of(
                sys("system"),
                assistant("call x"),
                toolResult("result x"),
                assistant("call y"),
                toolResult("result y"));
        SimpleContextCompactor compactor = new SimpleContextCompactor();

        for (int budget = 1; budget <= 200; budget++) {
            List<String> result = compactor.compact(history, budget);
            // Find any TOOL_RESULT in result; its preceding entry must be the matching ASSISTANT
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).startsWith("TOOL_RESULT: ")) {
                    assertThat(i > 0).as("budget=%d, pair split at %d", budget, i).isTrue();
                    assertThat(result.get(i - 1)).as("budget=%d, wrong pair at %d", budget, i)
                            .startsWith("ASSISTANT: ");
                }
            }
        }
    }
}
