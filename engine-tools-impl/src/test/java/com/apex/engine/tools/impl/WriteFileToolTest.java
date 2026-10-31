package com.apex.engine.tools.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WriteFileToolTest {

    @TempDir
    Path tempDir;

    WriteFileTool tool;

    @BeforeEach
    void setUp() throws Exception {
        tool = new WriteFileTool(new RealPathJail(tempDir));
    }

    @Test
    void writesNewFile() throws Exception {
        tool.execute(Map.of("path", "a.txt", "content", "hello"));

        assertThat(Files.readString(tempDir.resolve("a.txt"))).isEqualTo("hello");
    }

    @Test
    void overwritesExistingFile() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "old");

        tool.execute(Map.of("path", "a.txt", "content", "new"));

        assertThat(Files.readString(tempDir.resolve("a.txt"))).isEqualTo("new");
    }

    @Test
    void rejectsMissingParentDirectory() {
        assertThatThrownBy(() -> tool.execute(
                Map.of("path", "missing/a.txt", "content", "x")))
                .isInstanceOf(ToolException.class);
    }

    @Test
    void rejectsPathOutsideJail() {
        assertThatThrownBy(() -> tool.execute(
                Map.of("path", "/etc/apex.txt", "content", "x")))
                .isInstanceOf(ToolException.class);
    }

    @Test
    void rejectsMissingContentArgument() {
        assertThatThrownBy(() -> tool.execute(Map.of("path", "a.txt")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("missing arg");
    }
}
