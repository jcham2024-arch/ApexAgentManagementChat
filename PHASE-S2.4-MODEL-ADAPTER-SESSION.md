# PHASE-S2.4-MODEL-ADAPTER-SESSION.md — Session Package, Part 1 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 1 of 8:** reading protocol, context deltas, prerequisite, scope.
Parts: SESSION · CORE-PORT · LLM-BASICS · CONFIG · PROTOCOL · PROVIDER ·
METADATA-TESTS · CRITERIA, each `PHASE-S2.4-MODEL-ADAPTER-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S2-KERNEL.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S2.4

Authoritative where they conflict with any prior file. Do not stop
over them.

### 1.1 Spec not required

Router §4 says S2.4 needs spec Part 3 pasted. **Superseded.** The
adapter is self-contained: it consumes `ChatRequest`, `ChatResponse`,
`CancellationToken`, and the streaming primitive defined here. Layer 1
types are bridged in S2.7 E2E. Do not read spec parts. Do not create
spec files.

### 1.2 `ChatModelPort` gains a default streaming method

S2.1 defined `ChatModelPort.chat(ChatRequest)`. S2.4 adds:

```java
default void stream(ChatRequest request, CancellationToken token,
                    Consumer<ChatChunk> onChunk)
```

The default calls `chat()` once and emits one terminal chunk. Lambda
implementations still work (still a functional interface). This is
additive; do not modify `chat()`.

### 1.3 Streaming cancellation is best-effort

The loop checks `CancellationToken` between chunks. If the provider is
slow to send the next chunk, cancellation is delayed until the next
chunk arrives. Javadoc must state this. A future phase can close the
input stream from a watcher thread; S2.4 does not.

### 1.4 JSON parser is duplicated — do not consolidate

`engine-tools-impl` has a `Json` parser (S2.3) throwing
`ToolException`. This sub-phase adds a module-local `JsonParser` in
`engine-llm-http` throwing `LlmException`. Both files are self-
contained; they are **not** cross-importable across modules. The
duplication is intentional and temporary. A future refactor
consolidates them into `engine-core`. Do not extract now — that would
require touching S2.3.

**Note in both files:** a comment cross-referencing the sibling.

### 1.5 `ChatChunk` lives in `engine-core`

Streaming primitive is a record in
`com.apex.engine.core.port.model.ChatChunk`. The port method references
it; `engine-llm-http` uses it. Not in `engine-llm-http` — it is part
of the port contract.

### 1.6 `LlmException` is module-local

`LlmException extends RuntimeException` in `com.apex.engine.llm.http`.
The port interface does not declare it — the default `stream()` throws
nothing. Implementations throw `LlmException`; callers who care catch
it; others catch `RuntimeException`. This avoids a reverse dependency
from `engine-core` back to an adapter.

### 1.7 `System.getenv()` and `java.net.http` are allowed

`engine-llm-http` is Tier 1. The ArchUnit purity test applies only to
Tier 0 modules. `ProviderConfigLoader.fromEnv()` reads env vars
directly. Tests call `fromMap(Map)` instead, so no live env is
required.

## 2. Prerequisite

S2.3 merged to `main`. `mvn -B test` green on `main`. If not, stop
and report.

## 3. Scope

Deliver:

1. `ChatChunk` record in `engine-core` (port.model package)
2. `ChatModelPort` gains the default `stream()` method
3. Nine new files in `engine-llm-http`
4. Seven test classes across `engine-core` and `engine-llm-http`
5. Updated `package-info.java` and `README.md` for `engine-llm-http`
6. `docs/INVARIANTS.md` K6 row: note streaming cancellation test
