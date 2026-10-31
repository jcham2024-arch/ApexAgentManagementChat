# PHASE-S2.3-TOOLS-METADATA-TESTS.md — Session Package, Part 7 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 7 of 8:** §4.12 and §5.1–§5.3, metadata, JsonTest, RealPathJailTest, K2/K5 test.
Parts: SESSION · JSON · JAIL · FILE-TOOLS · RUN-COMMAND · HTTP-EXECUTOR ·
METADATA-TESTS · CONTRACTS-CRITERIA, each `PHASE-S2.3-TOOLS-<part>.md`.
Cut for the 200-line rule in `AGENT_RULES.md` §2.13.

---
### 4.12 Update `engine-tools-impl` metadata

**`engine-tools-impl/src/main/java/com/apex/engine/tools/impl/package-info.java`** — replace content:

```java
/**
 * Five kernel tools and the default ToolExecutorPort. Tier 1.
 * Implements: ToolExecutorPort. Consumes: java.base, java.net.http.
 * Proves K2 (no shell) and K5 (process tree kill) via tests.
 */
@NullMarked
package com.apex.engine.tools.impl;

import org.jspecify.annotations.NullMarked;
```

**`engine-tools-impl/README.md`** — replace content:

```markdown
# engine-tools-impl

**Tier:** 1 · **Purpose:** Five kernel tools and the default ToolExecutorPort.

## Allowed dependencies
java.base, java.net.http, jspecify

## Ports consumed
none

## Ports implemented
ToolExecutorPort (via KernelToolExecutor)

## Tools
- read_file — UTF-8 read, capped at 50 KB
- write_file — UTF-8 write, parent must exist inside workspace
- list_files — glob match, depth 1
- run_command — allowlisted binary, tokenized argv, 30s default / 120s max, tree kill on timeout
- http_get — public or loopback, link-local blocked

## Tests
JsonTest · RealPathJailTest · ReadFileToolTest · WriteFileToolTest ·
ListFilesToolTest · RunCommandToolTest (K2 + K5) · HttpGetToolTest ·
KernelToolExecutorTest
```

## 5. Tests

Package: `com.apex.engine.tools.impl` (mirror main).

### 5.1 `JsonTest.java`

Cover at minimum:

- `{}` → empty map
- `{"a":"b"}` → `{a=b}`
- `{"n":42}` → `{n=42L}`
- `{"f":3.14}` → `{f=3.14d}`
- `{"t":true}` / `{"t":false}` / `{"t":null}`
- `{"a":["x","y"]}` → array of strings
- `{"a":{"b":"c"}}` → nested object
- `{"s":"a\"b"}` → escape handling
- `{"s":"a\\nb"}` → newline escape
- Errors: empty string, `{`, `}`, `{"a"`, `{"a":}`, trailing content

### 5.2 `RealPathJailTest.java`

- `resolveExisting` accepts a file inside root
- `resolveExisting` rejects `../etc/passwd`
- `resolveExisting` rejects absolute path outside root
- `resolveExisting` rejects symlink pointing outside root (create a
  symlink to `/tmp` inside the workspace, verify rejection)
- `resolveForWrite` accepts a file whose parent is inside root
- `resolveForWrite` rejects `../newfile`

### 5.3 `RunCommandToolTest.java` — critical: K2 and K5

```java
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

    private static Path findBinary(String... candidates) {
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.isExecutable(p)) return p;
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
```
