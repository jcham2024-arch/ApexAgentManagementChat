# PHASE-S2.3-TOOLS-SESSION.md — Session Package, Part 1 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 1 of 8:** reading protocol, context deltas, prerequisite, scope, ToolException, KernelTool.
Parts: SESSION · JSON · JAIL · FILE-TOOLS · RUN-COMMAND · HTTP-EXECUTOR ·
METADATA-TESTS · CONTRACTS-CRITERIA, each `PHASE-S2.3-TOOLS-<part>.md`.
Cut for the 200-line rule in `AGENT_RULES.md` §2.13.

---
## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S2-KERNEL.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S2.3

Authoritative where they conflict with any prior file. Do not stop
over them.

### 1.1 Spec is not needed

Router §4 says S2.3 does not need `docs/apex-chat-spec/`. That holds.
The tools use only the S2.1 ports and JDK. Do not read spec parts. Do
not create spec files.

### 1.2 No JSON library is available

Jackson is banned in all tiers. The tools need to parse the
`ToolCall.argumentsJson` string, so a **minimal JSON parser is written
in this sub-phase** (`Json.java`, ~170 lines). It handles objects,
arrays, strings, numbers, booleans, null. It is not a security
boundary: every parsed value is validated by the specific tool after
parsing (paths via `RealPathJail`, command args passed as literal
`List<String>` bytes).

### 1.3 Where each tool lives

All tools go in `engine-tools-impl` (Tier 1). They share a local
`KernelTool` interface defined in the same module. The `engine-tools`
(Tier 0) module stays empty — it will hold tool metadata in a later
phase. Do not add code to `engine-tools` in S2.3.

### 1.4 `ToolCall.argumentsJson` is the wire format

The S2.1 port fixes the `ToolCall` shape: `(id, toolName,
argumentsJson)`. The executor parses `argumentsJson` once and dispatches
to the specific tool with typed args. Do not change the port.

### 1.5 Command execution is allowlisted

`RunCommandTool` takes a `Map<String, Path>` at construction (tool
name → absolute binary path). The CLI (S2.6) will populate it. For
S2.3 tests, use a small registry with `echo`, `bash`, `sleep` — the
minimum needed to prove K2 and K5.

### 1.6 HTTP allows loopback, blocks link-local

`HttpGetTool` permits loopback (needed for local model endpoints, S8
SBC nodes) and public addresses. It blocks link-local (`169.254.0.0/16`,
`fe80::/10`) and `0.0.0.0` / `::` (the AWS metadata attack surface).
S3 will harden this with a per-tenant allowlist; do not add config here.

### 1.7 `engine-tools-impl/package-info.java` and `README.md` update

The S2.0 versions say "Ports consumed: none". This sub-phase makes the
module implement `ToolExecutorPort`. Update both to reflect that.

## 2. Prerequisite

S2.2 merged to `main`. `engine-planner`, `engine-core` exist. `mvn -B
test` green. If not, stop and report.

## 3. Scope

Deliver:

1. Eleven source files in `engine-tools-impl`
2. Seven test classes
3. Updated `package-info.java` and `README.md` for `engine-tools-impl`
4. `docs/INVARIANTS.md` updates: K2 and K5 rows gain test names
5. No POM changes (existing deps are sufficient)

## 4. Source files

Package: `com.apex.engine.tools.impl`.

### 4.1 `ToolException.java`

```java
package com.apex.engine.tools.impl;

/**
 * Raised by a tool on failure. Carries a category (used in logs and
 * for later classification) and a human-readable message. The
 * executor converts it to a {@code ToolResult} with {@code isError =
 * true}.
 */
public final class ToolException extends RuntimeException {
    private final String category;

    public ToolException(String category, String message) {
        super(message);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
```

### 4.2 `KernelTool.java`

```java
package com.apex.engine.tools.impl;

import java.util.Map;

/**
 * A single kernel tool. Implementations are stateless per call: all
 * configuration is captured at construction, and {@link #execute}
 * derives everything else from {@code args}.
 */
public interface KernelTool {
    String name();
    String execute(Map<String, Object> args);
}
```
