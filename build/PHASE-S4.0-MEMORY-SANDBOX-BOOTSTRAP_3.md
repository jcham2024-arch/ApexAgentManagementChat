# PHASE-S4.0-MEMORY-SANDBOX-BOOTSTRAP.md

**Version:** 1.0 · **Sub-phase of S4** · **Ends with module skeletons, enums, and interfaces compiling; `mvn test` green.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S4-MEMORY-SANDBOX.md` (repo — router)
4. **This file**
 * {@code output} is the merged stdout+stderr, capped at the sandbox's
 * output limit. {@code timedOut} is true if the sandbox was killed for
 * exceeding its timeout.
 */
public record SandboxResult(int exitCode, String output, boolean timedOut) {
    public SandboxResult {
        Objects.requireNonNull(output, "output");
    }
}
```

**SandboxPort.java**
```java
package com.apex.engine.sandbox;

/**
 * Executes a command inside a specific isolation tier. Each tier has
 * one implementation; the caller selects the tier by the tool's risk
 * level. Implementations are responsible for their own isolation
 * mechanism (ProcessBuilder, Docker+gVisor, Firecracker). Tier 1.
 */
public interface SandboxPort {
    SandboxTier tier();
    SandboxResult execute(SandboxCommand command);
}
```

**package-info.java**
```java
/**
 * Sandboxed tool execution across three isolation tiers. Tier 1.
 * Defines the sandbox port and command/result types. Process tier
 * lands in S4.6; container and Firecracker stubs land in S4.6;
 * production isolation lands in S6.
 */
@NullMarked
package com.apex.engine.sandbox;

import org.jspecify.annotations.NullMarked;
```

**README.md**
```markdown
# engine-sandbox

**Tier:** 1 · **Purpose:** Sandboxed tool execution across isolation tiers.

## Allowed dependencies
java.base (including java.nio.file), jspecify

## Ports consumed
none in S4.0 — S4.6 will consume SandboxedProcessExecutor

## Ports implemented
SandboxPort (per tier) — implementations land in S4.6

## Types
- `SandboxTier` — PROCESS / CONTAINER / FIRE_CRACKER
- `SandboxCommand` — binary, args, cwd, timeout
- `SandboxResult` — exit code, output, timedOut
- `SandboxPort` — per-tier execution interface

## Tests
SandboxTypesTest
```

### 4.6 Tests

**`engine-memory/src/test/java/com/apex/engine/memory/MemoryTypesTest.java`**

Cover:
1. `MemoryQuery` rejects null text
2. `MemoryQuery` rejects blank text
3. `MemoryQuery` rejects empty kinds
4. `MemoryQuery` rejects `maxHits <= 0`
5. `MemoryQuery` defensively copies kinds (`UnsupportedOperationException` on mutate)
6. `MemoryHit` rejects null kind/source/content
7. `MemoryHit` rejects `score < 0`
8. `MemoryHit` rejects blank source
9. `MemoryResult` defensively copies hits and queried
10. `MemoryResult` accepts empty hits with non-empty queried

**`engine-sandbox/src/test/java/com/apex/engine/sandbox/SandboxTypesTest.java`**

Cover:
1. `SandboxCommand` rejects null binaryPath/args/cwd
2. `SandboxCommand` rejects relative binaryPath
3. `SandboxCommand` rejects relative cwd
4. `SandboxCommand` rejects `timeoutSeconds <= 0`
5. `SandboxCommand` defensively copies args
6. `SandboxResult` rejects null output
7. `SandboxResult` accepts any exit code (including negative)
8. `SandboxTier` has exactly three values (PROCESS, CONTAINER, FIRE_CRACKER)

### 4.7 `CONTEXT.md` §4

Add two rows to the module table:

| Module | Tier | Role |
|---|---|---|
| `engine-memory` | 0 | Memory types, router, compactor |
| `engine-sandbox` | 1 | Sandbox port, tier implementations |

If the table uses a different column layout, match it. Do not
restructure.

## 5. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] Both new modules build and are in `apex/pom.xml` `<modules>`.
- [ ] Six memory types exist in `com.apex.engine.memory`.
- [ ] Four sandbox types exist in `com.apex.engine.sandbox`.
- [ ] `MemoryTypesTest` passes all 10 tests.
- [ ] `SandboxTypesTest` passes all 8 tests.
- [ ] Enforcer passes on both modules (Tier 0 block on `engine-memory`,
      Tier 1 block on `engine-sandbox`).
- [ ] `jdeps` gate (Tier 0 only) still passes on `engine-memory` jar.
- [ ] `CONTEXT.md` §4 gains two rows.
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency anywhere.
- [ ] `docs/INVARIANTS.md` unchanged.
- [ ] No spec files read or created.
- [ ] No other module modified.

## 6. Do NOT add

- Any implementation of `MemoryRouter`, `ContextCompactor`, or
  `SandboxPort`
- Any dependency on `engine-core`, `engine-tools-impl`,
  `engine-store-sqlite`, or `engine-cli`
- Any Spring, Jackson, Micrometer, Google, or Embabel dependency
- Any change to `apex-chat/*`, `apex-core/*`, `apex-platform/*`, or
  `engine-cli`
- Any spec file
- Any file not listed in §4 or §5

## 7. Commit

Three commits on `phase/s4.0-memory-sandbox-bootstrap`:

1. `build(S4.0): add engine-memory and engine-sandbox modules`
2. `feat(S4.0): add memory types and interfaces (router, compactor)`
3. `feat(S4.0): add sandbox types and interface; update CONTEXT.md`

Push. Report CI link, per-test-class pass counts, and confirm:
- Both new modules appear in `apex/pom.xml` `<modules>`
- `MemoryTypesTest` and `SandboxTypesTest` pass
- No enterprise dependency added
- `CONTEXT.md` §4 updated

If CI fails twice on the same root cause, stop and report the log.

---

**End of S4.0 session package.**