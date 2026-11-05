package com.apex.engine.tools.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadFileToolTest {

    @TempDir
    Path tempDir;

    ReadFileTool tool;

    @BeforeEach
    void setUp() throws Exception {
        tool = new ReadFileTool(new RealPathJail(tempDir));
    }

    @Test
    void readsFileContent() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello");

        assertThat(tool.execute(Map.of("path", "a.txt"))).isEqualTo("hello");
    }

    @Test
    void rejectsDirectory() throws Exception {
        Files.createDirectory(tempDir.resolve("dir"));

        assertThatThrownBy(() -> tool.execute(Map.of("path", "dir")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("not a file");
    }

    @Test
    void rejectsFileOverSizeCap() throws Exception {
        Files.writeString(tempDir.resolve("big.txt"), "x".repeat(50_001));

        assertThatThrownBy(() -> tool.execute(Map.of("path", "big.txt")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void rejectsPathOutsideJail() {
        assertThatThrownBy(() -> tool.execute(Map.of("path", "/etc/hosts")))
                .isInstanceOf(ToolException.class);
    }

    @Test
    void rejectsMissingArgument() {
        assertThatThrownBy(() -> tool.execute(Map.of()))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("missing arg");
    }
}
