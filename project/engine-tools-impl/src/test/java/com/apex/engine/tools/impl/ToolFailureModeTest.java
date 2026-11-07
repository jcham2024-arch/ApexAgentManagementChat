package com.apex.engine.tools.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failure-mode tests for kernel tools. Non-zero exits, missing
 * binaries, and inaccessible working directories must all produce
 * predictable ToolExceptions rather than crashing the plan loop.
 */
class ToolFailureModeTest {

    @TempDir Path tempDir;
    RunCommandTool tool;
    Path echoBinary;

    @BeforeEach
    void setUp() throws Exception {
        echoBinary = findBinary("/bin/echo", "/usr/bin/echo");
        RealPathJail jail = new RealPathJail(tempDir);
        tool = new RunCommandTool(Map.of("echo", echoBinary), jail);
    }

    @Test
    void nonZeroExitIsError() throws Exception {
        // /bin/echo always exits 0; use /bin/false if available, or a
        // script that exits 1.
        Path falseBinary = findBinary("/bin/false", "/usr/bin/false");
        if (falseBinary == null) {
            return; // skip on hosts without /bin/false
        }
        RealPathJail jail = new RealPathJail(tempDir);
        RunCommandTool t = new RunCommandTool(Map.of("false", falseBinary), jail);

        assertThatThrownBy(() -> t.execute(Map.of("cmd", "false")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("exit");
    }

    @Test
    void missingBinaryIsRejected() {
        assertThatThrownBy(() -> tool.execute(Map.of("cmd", "nonexistent-cmd")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("command not allowed");
    }

    @Test
    void inaccessibleCwdIsRejected() {
        assertThatThrownBy(() -> tool.execute(Map.of(
                "cmd", "echo", "args", List.of("hi"), "cwd", "/nonexistent")))
                .isInstanceOf(ToolException.class)
                .satisfies(e -> assertThat(((ToolException) e).category())
                        .isEqualTo("path"));
    }

    @Test
    void missingCmdArgIsRejected() {
        assertThatThrownBy(() -> tool.execute(Map.of()))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("missing arg");
    }

    @Test
    void argsNotArrayIsRejected() {
        assertThatThrownBy(() -> tool.execute(Map.of("cmd", "echo", "args", "not-an-array")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("arg not array");
    }

    private static Path findBinary(String... candidates) {
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.isExecutable(p)) return p;
        }
        return null;
    }
}
