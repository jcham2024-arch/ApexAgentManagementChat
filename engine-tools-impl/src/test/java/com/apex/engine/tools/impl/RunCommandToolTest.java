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

class RunCommandToolTest {

    @TempDir Path tempDir;
    RunCommandTool tool;
    Path echoBinary;
    Path bashBinary;

    @BeforeEach
    void setUp() throws Exception {
        echoBinary = findBinary("/bin/echo", "/usr/bin/echo");
        bashBinary = findBinary("/bin/bash", "/usr/bin/bash", "/bin/sh");
        RealPathJail jail = new RealPathJail(tempDir);
        tool = new RunCommandTool(
                Map.of("echo", echoBinary, "bash", bashBinary), jail);
    }

    @Test
    void runsSimpleCommand() {
        String out = tool.execute(Map.of(
                "cmd", "echo", "args", List.of("hello", "world")));
        assertThat(out.trim()).isEqualTo("hello world");
    }

    @Test
    void doesNotInterpretShellMetacharacters_K2() {
        String payload = "; rm -rf / ; echo INJECTED";
        String out = tool.execute(Map.of(
                "cmd", "echo", "args", List.of(payload)));
        assertThat(out).contains(payload);
        assertThat(out).doesNotContain("INJECTED\nINJECTED");
        assertThat(out.trim()).isEqualTo(payload);
    }

    @Test
    void rejectsCommandNotInRegistry() {
        assertThatThrownBy(() -> tool.execute(Map.of("cmd", "curl")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("command not allowed");
    }

    @Test
    void enforcesTimeoutAndKillsTree_K5() throws Exception {
        Path script = tempDir.resolve("spawner.sh");
        Files.writeString(script, "#!/bin/bash\nsleep 287 &\nsleep 287\n");
        script.toFile().setExecutable(true);

        long before = countBashWithScript(script);
        assertThatThrownBy(() -> tool.execute(Map.of(
                "cmd", "bash",
                "args", List.of(script.toString()),
                "timeoutSec", 1)))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("timeout");

        Thread.sleep(1500);
        long after = countBashWithScript(script);
        assertThat(after).isEqualTo(before);
    }

    /**
     * Finds a binary at the usual Unix locations, falling back to a PATH
     * lookup. The fallback is a small deviation from the phase file: it
     * keeps the K2 and K5 proofs runnable on hosts without /bin/echo or
     * /bin/bash (Android, minimal containers) instead of erroring in
     * setUp. The proofs themselves are unchanged.
     */
    private static Path findBinary(String... candidates) {
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.isExecutable(p)) return p;
        }
        String name = Path.of(candidates[0]).getFileName().toString();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) {
                if (dir.isBlank()) continue;
                Path p = Path.of(dir).resolve(name);
                if (Files.isExecutable(p)) return p;
            }
        }
        throw new IllegalStateException("no binary found among: " + List.of(candidates));
    }

    private static long countBashWithScript(Path script) {
        return ProcessHandle.allProcesses()
                .filter(ProcessHandle::isAlive)
                .filter(p -> p.info().commandLine()
                        .map(c -> c.contains(script.toString()))
                        .orElse(false))
                .count();
    }
}
