package com.apex.engine.tools.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealPathJailTest {

    @TempDir
    Path tempDir;

    RealPathJail jail;

    @BeforeEach
    void setUp() throws Exception {
        jail = new RealPathJail(tempDir);
    }

    @Test
    void resolvesExistingFileInsideRoot() throws Exception {
        Path file = Files.writeString(tempDir.resolve("a.txt"), "x");

        assertThat(jail.resolveExisting("a.txt")).isEqualTo(file.toRealPath());
    }

    @Test
    void rejectsParentTraversal() throws Exception {
        String name = tempDir.getFileName() + "-outside.txt";
        Path outside = Files.writeString(tempDir.resolveSibling(name), "x");

        assertThat(outside).exists();
        assertThatThrownBy(() -> jail.resolveExisting("../" + name))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("escapes workspace");
        assertThatThrownBy(() -> jail.resolveExisting("../etc/passwd"))
                .isInstanceOf(ToolException.class);
    }

    @Test
    void rejectsAbsolutePathOutsideRoot() {
        assertThatThrownBy(() -> jail.resolveExisting("/etc"))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("escapes workspace");
    }

    @Test
    void rejectsSymlinkEscapingRoot() throws Exception {
        Path outside = Files.createDirectory(
                tempDir.resolveSibling(tempDir.getFileName() + "-outside"));
        Files.createSymbolicLink(tempDir.resolve("escape"), outside);

        assertThatThrownBy(() -> jail.resolveExisting("escape"))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("escapes workspace");
    }

    @Test
    void resolveForWriteAcceptsFileInsideRoot() {
        assertThat(jail.resolveForWrite("new.txt"))
                .isEqualTo(jail.canonicalRoot().resolve("new.txt"));
    }

    @Test
    void resolveForWriteRejectsSymlinkEscapingRoot() throws Exception {
        Path outside = Files.createDirectory(
                tempDir.resolveSibling(tempDir.getFileName() + "-write-outside"));
        Files.createSymbolicLink(tempDir.resolve("write-escape"), outside);

        assertThatThrownBy(() -> jail.resolveForWrite("write-escape"))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("escapes workspace");
    }

    @Test
    void resolveForWriteRejectsParentTraversal() {
        assertThatThrownBy(() -> jail.resolveForWrite("../new.txt"))
                .isInstanceOf(ToolException.class);
    }

    @Test
    void resolveForWriteRejectsBlankPath() {
        assertThatThrownBy(() -> jail.resolveForWrite("   "))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("path is empty");
    }
}
