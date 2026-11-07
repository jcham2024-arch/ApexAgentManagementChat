# PHASE-S2.3-TOOLS-FILE-TOOLS.md — Session Package, Part 4 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 4 of 8:** §4.6–§4.8, read_file, write_file, list_files.
Parts: SESSION · JSON · JAIL · FILE-TOOLS · RUN-COMMAND · HTTP-EXECUTOR ·
METADATA-TESTS · CONTRACTS-CRITERIA, each `PHASE-S2.3-TOOLS-<part>.md`.
Cut for the 200-line rule in `AGENT_RULES.md` §2.13.

---
### 4.6 `ReadFileTool.java`

```java
package com.apex.engine.tools.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Reads a UTF-8 text file from inside the workspace. Rejects files
 * larger than 50 KB. Args: {@code path} (string, required).
 */
public final class ReadFileTool implements KernelTool {

    private static final long MAX_BYTES = 50_000L;
    private final RealPathJail jail;

    public ReadFileTool(RealPathJail jail) {
        this.jail = Objects.requireNonNull(jail, "jail");
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object v = args.get("path");
        if (!(v instanceof String s) || s.isBlank()) {
            throw new ToolException("read_file", "missing arg: path");
        }
        Path file = jail.resolveExisting(s);
        if (!Files.isRegularFile(file)) {
            throw new ToolException("read_file", "not a file: " + file);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_BYTES) {
                throw new ToolException("read_file", "file too large: " + size + " bytes");
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolException("read_file", "read failed: " + e.getMessage());
        }
    }
}
```

### 4.7 `WriteFileTool.java`

```java
package com.apex.engine.tools.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Writes a UTF-8 text file inside the workspace. Parent directory must
 * exist and be inside the root. Args: {@code path}, {@code content}
 * (both strings, required).
 */
public final class WriteFileTool implements KernelTool {

    private final RealPathJail jail;

    public WriteFileTool(RealPathJail jail) {
        this.jail = Objects.requireNonNull(jail, "jail");
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String execute(Map<String, Object> args) {
        String path = requireString(args, "path");
        String content = requireString(args, "content");
        Path file = jail.resolveForWrite(path);
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return "wrote " + content.length() + " chars to " + file;
        } catch (IOException e) {
            throw new ToolException("write_file", "write failed: " + e.getMessage());
        }
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (!(v instanceof String s)) {
            throw new ToolException("write_file", "missing arg: " + key);
        }
        return s;
    }
}
```

### 4.8 `ListFilesTool.java`

```java
package com.apex.engine.tools.impl;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lists files in a workspace directory matching a glob. Depth 1 only.
 * Args: {@code dir} (string, required), {@code pattern} (string,
 * optional, default {@code *}). Output: newline-separated paths
 * relative to {@code dir}, sorted.
 */
public final class ListFilesTool implements KernelTool {

    private final RealPathJail jail;

    public ListFilesTool(RealPathJail jail) {
        this.jail = Objects.requireNonNull(jail, "jail");
    }

    @Override
    public String name() {
        return "list_files";
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object dv = args.get("dir");
        if (!(dv instanceof String dirStr) || dirStr.isBlank()) {
            throw new ToolException("list_files", "missing arg: dir");
        }
        Object pv = args.get("pattern");
        String pattern = pv instanceof String s ? s : "*";

        Path dir = jail.resolveExisting(dirStr);
        if (!Files.isDirectory(dir)) {
            throw new ToolException("list_files", "not a directory: " + dir);
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> matcher.matches(p.getFileName()))
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new ToolException("list_files", "list failed: " + e.getMessage());
        }
    }
}
```
