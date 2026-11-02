package com.apex.engine.tools.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListFilesToolTest {

    @TempDir
    Path tempDir;

    ListFilesTool tool;

    @BeforeEach
    void setUp() throws Exception {
        tool = new ListFilesTool(new RealPathJail(tempDir));
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.md"), "b");
    }

    @Test
    void listsEveryEntryByDefault() {
        String listing = tool.execute(Map.of("dir", "."));

        assertThat(listing).contains("a.txt").contains("b.md");
    }

    @Test
    void filtersByGlob() {
        String listing = tool.execute(Map.of("dir", ".", "pattern", "*.txt"));

        assertThat(listing).contains("a.txt").doesNotContain("b.md");
    }

    @Test
    void rejectsNonDirectory() {
        assertThatThrownBy(() -> tool.execute(Map.of("dir", "a.txt")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void rejectsPathOutsideJail() {
        assertThatThrownBy(() -> tool.execute(Map.of("dir", "/etc")))
                .isInstanceOf(ToolException.class);
    }

    @Test
    void rejectsMissingDirectoryArgument() {
        assertThatThrownBy(() -> tool.execute(Map.of()))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("missing arg");
    }
}
