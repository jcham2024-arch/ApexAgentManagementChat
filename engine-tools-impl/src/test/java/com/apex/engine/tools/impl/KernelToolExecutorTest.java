package com.apex.engine.tools.impl;

import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KernelToolExecutorTest {

    @TempDir
    Path tempDir;

    ToolExecutorPort executor;

    @BeforeEach
    void setUp() throws Exception {
        executor = new KernelToolExecutor(new RealPathJail(tempDir), Map.of());
    }

    @Test
    void unknownToolReturnsErrorResult() {
        ToolResult result = executor.execute(new ToolCall("c1", "nope", "{}"));

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("unknown tool");
        assertThat(result.callId()).isEqualTo("c1");
    }

    @Test
    void malformedArgumentsReturnErrorResult() {
        ToolResult result = executor.execute(new ToolCall("c2", "read_file", "{"));

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).isNotBlank();
    }

    @Test
    void readFileCallReturnsContent() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello");

        ToolResult result = executor.execute(
                new ToolCall("c3", "read_file", "{\"path\":\"a.txt\"}"));

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).isEqualTo("hello");
        assertThat(result.callId()).isEqualTo("c3");
    }

    @Test
    void toolFailureReturnsErrorResultNotException() {
        ToolResult result = executor.execute(
                new ToolCall("c4", "read_file", "{\"path\":\"absent.txt\"}"));

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).isNotBlank();
    }

    @Test
    void durationIsRecorded() {
        ToolResult result = executor.execute(new ToolCall("c5", "nope", "{}"));

        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0L);
    }
}
