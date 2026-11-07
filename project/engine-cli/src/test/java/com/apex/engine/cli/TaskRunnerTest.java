package com.apex.engine.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRunnerTest {

    private static TaskDefinition task() {
        return new TaskDefinition("t-1", "d", "i", 3, "", List.of("read_file"),
                List.of(), List.of(), List.of());
    }

    @Test
    void stubReturnsFixedResult() {
        TaskRunResult result = new StubTaskRunner().run(task());

        assertThat(result.taskId()).isEqualTo("t-1");
        assertThat(result.status()).isEqualTo("STUB");
        assertThat(result.steps()).isZero();
        assertThat(result.durationMs()).isZero();
    }

    @Test
    void resultSerializesToJson() {
        assertThat(new TaskRunResult("t-1", "STUB", 0, 0L).toJson())
                .isEqualTo("{\"task_id\":\"t-1\",\"status\":\"STUB\","
                        + "\"steps\":0,\"duration_ms\":0}");
    }

    @Test
    void resultEscapesJsonHostileTaskId() {
        assertThat(new TaskRunResult("a\"b\n", "STUB", 1, 2L).toJson())
                .contains("a\\\"b\\n");
    }
}
