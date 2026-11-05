# Harness Interoperability Layer — Implementation Instructions

**Audience:** Engineering team working on `Google-Ai-repo`
**Scope:** Take the codebase from "closed custom tool loop" to "open agent harness" that can use MCP, Claude Skills, OpenAI function calling, and directive files.
**Guiding constraints:** Do **not** break the two invariants enforced by CI:

1. `:core-engine` remains pure Java 11 with **zero runtime dependencies**.
2. All external interaction continues to flow through **port interfaces** — no adapter leaks into the engine.

Work through the phases in order. Each phase ends with a working build, tests, and a short PR. Do not begin a phase until the previous phase's acceptance criteria are met.

---

## Phase 1 — Upgrade the Tool Abstraction

**Goal:** Replace the primitive `ToolWorker` model with a rich `ToolDefinition` that can carry JSON Schema, risk metadata, source identity, and transport hints — without breaking existing tools.

### 1.1 Add the canonical descriptor

Create `core-engine/src/main/java/.../resources/ToolDefinition.java`:

```java
public final class ToolDefinition {
    public enum Source { NATIVE, MCP, OPENAI, CLAUDE_SKILL, AGENTRULES, APPFUNCTIONS }
    public enum Kind   { TOOL, SKILL, DIRECTIVE }

    private final String id;                // canonical: "mcp://srv/name"
    private final String name;
    private final String description;
    private final Kind kind;
    private final Source source;
    private final MiniJson.JsonNode inputSchema;  // JSON Schema object, or null
    private final RiskLevel risk;                 // L0–L4, assigned by RiskAnnotator
    private final TransportHint transport;
    private final Map<String,String> metadata;    // annotations, strict, version, etc.
}
```

Add a `TransportHint` value object:

```java
public final class TransportHint {
    public enum Type { LOCAL, JSONRPC_HTTP, JSONRPC_SSE, HTTP_REST, MODEL_ATTACHED }
    private final Type type;
    private final String endpoint;             // null for LOCAL / MODEL_ATTACHED
    private final Map<String,String> headers;  // auth, content-type
}
```

### 1.2 Add the `ResourceBundle` container

```java
public final class ResourceBundle {
    private final List<ToolDefinition> tools;
    private final List<ToolDefinition> skills;
    private final List<ToolDefinition> directives;
}
```

### 1.3 Wrap existing tools — do not rewrite them

Create `app/src/main/java/.../tools/NativeToolAdapter.java`:

```java
public final class NativeToolAdapter {
    public static ToolDefinition wrap(ToolWorker worker, String schemaJson, RiskLevel risk) {
        return new ToolDefinition(
            "native://" + worker.getName(),
            worker.getName(),
            worker.getDescription(),
            ToolDefinition.Kind.TOOL,
            ToolDefinition.Source.NATIVE,
            MiniJson.parse(schemaJson),
            risk,
            TransportHint.local(),
            Map.of()
        );
    }
}
```

Register **every existing `ToolWorker`** through this adapter at startup. Do **not** modify the `ToolWorker` interface — the adapter is the compatibility shim.

### 1.4 Extend `ToolRegistry`

Add methods to `ToolRegistry`:

- `void register(ToolDefinition def)`
- `List<ToolDefinition> list()`
- `ToolDefinition resolve(String id)`
- `ResourceBundle bundle()` — returns the current state as a bundle.

Keep the existing `ToolWorker` registration path working so old code still compiles.

### Acceptance Criteria — Phase 1

- [ ] `ToolDefinition`, `TransportHint`, `ResourceBundle` exist in `:core-engine`.
- [ ] Every existing `ToolWorker` is wrapped by `NativeToolAdapter` and appears in `ToolRegistry.list()`.
- [ ] `ToolRegistry.bundle()` returns a populated `ResourceBundle`.
- [ ] `:core-engine` still has zero runtime dependencies (CI passes).
- [ ] New unit tests cover: wrapping a `ToolWorker`, resolving by id, schema round-trip through `MiniJson`.

---

## Phase 2 — Implement OpenAI Function Calling

**Goal:** Make the harness actually tell models which tools exist, using the standard `tools` array, and correctly parse `tool_calls` back.

### 2.1 Build the `tools` payload

In `app/src/main/java/.../provider/OpenAiCompatibleClient.java`, extend the request builder to include:

```java
{
  "model": "<model>",
  "messages": [...],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "<name>",
        "description": "<description>",
        "parameters": { /* JSON Schema from ToolDefinition.inputSchema */ },
        "strict": true
      }
    }
  ],
  "tool_choice": "auto"
}
```

Rules:

- Only include `ToolDefinition`s with `kind == TOOL` **and** a non-null `inputSchema`.
- `strict` is `true` only when `metadata["strict"] == "true"` **or** the schema is fully specified (no free-form `additionalProperties`).
- If `tools` is empty, omit the field entirely.

### 2.2 Parse the response

Extend `ChatResponse` and the response parser to handle:

- Single `tool_call`
- **Parallel** `tool_calls` (array)
- Arguments as a JSON string that must be parsed into a structured map
- Malformed JSON arguments — surface a structured `ToolCallParseException`, do not crash the loop.

```java
public final class ToolCall {
    private final String id;             // provider-assigned call id
    private final String toolId;         // maps to ToolDefinition.id
    private final MiniJson.JsonNode args;
}
```

### 2.3 Validate arguments before dispatch

In the orchestrator, before calling `ToolExecutorPort.execute(...)`, validate `args` against `ToolDefinition.inputSchema` using a small JSON Schema validator in `:core-engine` (hand-rolled, zero-dependency, supports the subset used by MCP/OpenAI: `type`, `properties`, `required`, `enum`, `items`, `oneOf`, `anyOf`, `additionalProperties`).

If validation fails: return a structured tool error to the model so it can retry, and record the failure in the audit log.

### 2.4 Round-trip the `tool` role message

After execution, append a `{"role":"tool","tool_call_id":"<id>","content":"<result>"}` message to the conversation before the next model call. This is required for OpenAI-compatible providers.

### Acceptance Criteria — Phase 2

- [ ] Request builder emits a valid `tools` array when tools are registered.
- [ ] Response parser handles single and parallel `tool_calls`.
- [ ] Arguments are schema-validated before execution.
- [ ] Tool results are appended as `role:"tool"` messages.
- [ ] Integration test against at least one OpenAI-compatible provider (Groq, Gemini via OpenRouter, or a local Ollama with tool support) proving end-to-end tool use.
- [ ] Audit log records tool calls with parsed arguments.

---

## Phase 3 — Build the MCP Client and Translator Layer

**Goal:** Ingest tools from a remote MCP server via JSON-RPC, expose them to the model as `ToolDefinition`s, and dispatch `tools/call` correctly.

### 3.1 The translator container (composition, not inheritance)

Create `core-engine/src/main/java/.../resources/ResourceFormatAdapter.java`:

```java
public interface ResourceFormatAdapter {
    ToolDefinition.Source sourceType();
    ResourceBundle ingest(String payload) throws TranslationException;
    String emit(ResourceBundle bundle) throws TranslationException;
}
```

Create `ResourceTranslator.java`:

```java
public final class ResourceTranslator {
    private final Map<ToolDefinition.Source, ResourceFormatAdapter> adapters;
    private final RiskAnnotator riskAnnotator;
    private final SchemaValidator schemaValidator;

    public ResourceBundle ingest(ToolDefinition.Source src, String payload) { ... }
    public String emit(ToolDefinition.Source src, ResourceBundle bundle) { ... }
}
```

The translator **has** adapters (composition). Adding a new standard = adding a new file, not editing the translator.

### 3.2 The MCP adapter

Create `app/src/main/java/.../mcp/McpFormatAdapter.java`:

- `ingest(String jsonrpcResponse)`:
  - Parse `result.tools[]`.
  - Map each to `ToolDefinition` with:
    - `source = MCP`
    - `id = "mcp://<server-id>/<name>"`
    - `inputSchema` copied verbatim
    - `metadata` populated with `annotations` (`readOnlyHint`, `destructiveHint`, `openWorldHint`)
    - `transport = JSONRPC_HTTP` or `JSONRPC_SSE`
  - Return a `ResourceBundle`.
- `emit(...)`:
  - Build `{"tools":[{name, description, inputSchema}]}` for the `tools/list` response when acting as a server.

### 3.3 The JSON-RPC transport

Create `app/src/main/java/.../mcp/McpTransportClient.java`:

- Minimal JSON-RPC 2.0 over HTTP using `HttpURLConnection` and `MiniJson` (zero dependency).
- Methods:
  - `initialize()` — send `{"jsonrpc":"2.0","method":"initialize",...}`
  - `listTools()` — `tools/list`
  - `callTool(name, args)` — `tools/call`
- Handle: request ids, error responses, SSE framing if the server streams.

### 3.4 The ingress container

Create `app/src/main/java/.../mcp/ResourceIngress.java`:

```java
public final class ResourceIngress implements ToolProviderPort {
    public void loadFromMcp(String serverId, String endpoint) {
        ResourceBundle bundle = translator.ingest(MCP, transport.listTools());
        registry.registerAll(bundle);
    }
}
```

### 3.5 The invocation bridge

Create `app/src/main/java/.../mcp/InvocationBridge.java`:

```java
public final class InvocationBridge implements ToolExecutorPort {
    @Override
    public ExecutionResult execute(ToolCall call) {
        ToolDefinition d = registry.resolve(call.toolId());

        // 1. Security gate — unchanged from current behavior
        if (d.risk().ordinal() >= RiskLevel.L3.ordinal()) {
            if (!security.approve(d, call).isApproved()) return ExecutionResult.denied(d);
        }

        // 2. Route by transport
        if (d.transport().type() == LOCAL) {
            return registry.executeNative(d, call.args());
        }
        return mcpClient.callTool(d, call.args());
    }
}
```

### 3.6 Risk annotator

Create `core-engine/src/main/java/.../resources/RiskAnnotator.java`:

Rules (applied in order, last wins):

1. Default unknown MCP tools to **`L3`** (matches existing fail-safe).
2. If `metadata["readOnlyHint"] == "true"` → clamp to `L2`.
3. If `metadata["destructiveHint"] == "true"` → force **`L4`**.
4. Local policy override table can raise (never lower) the level.

### Acceptance Criteria — Phase 3

- [ ] `ResourceFormatAdapter` and `ResourceTranslator` exist; translator uses composition.
- [ ] `McpFormatAdapter` ingests a real `tools/list` payload from a test server.
- [ ] `McpTransportClient` completes `initialize` → `tools/list` → `tools/call` against a mock server.
- [ ] `InvocationBridge` routes calls correctly and honors the security interlock.
- [ ] `RiskAnnotator` tests cover read-only, destructive, and unknown cases.
- [ ] End-to-end test: model selects an MCP tool, arguments validate, call dispatches, result returns to model.
- [ ] `:core-engine` still has zero runtime dependencies.

---

## Phase 4 — Claude Skills and Directive Files

**Goal:** Treat Skills and rule files as first-class resources that flow through the same translator pipeline.

### 4.1 The Claude Skill adapter

Create `app/src/main/java/.../claude/ClaudeSkillFormatAdapter.java`:

- `ingest(manifestJson)`:
  - Parse `{id, name, description, version}`.
  - Emit `ToolDefinition` with `kind = SKILL`, `source = CLAUDE_SKILL`, `transport = MODEL_ATTACHED`, `inputSchema = null`.
  - Store `metadata["digest"] = sha256(body)`.
- `emit(bundle)`:
  - Produce the Messages API attachment block:
    ```json
    { "skills": [ { "type": "custom", "skill_id": "<id>" } ] }
    ```

### 4.2 The Claude client

Create `app/src/main/java/.../claude/ClaudeClient.java` implementing `ChatModelPort`:

- Send the `skills` attachment block from the bundle on every request.
- Skills execute **model-side**; the harness does not run them locally.
- Parse the response's text and any tool-related blocks normally.

### 4.3 The directive adapter

Create `core-engine/src/main/java/.../resources/AgentRulesFormatAdapter.java`:

- `ingest(markdownOrToml)`:
  - Split into rules.
  - Emit one `ToolDefinition` per rule with `kind = DIRECTIVE`, `source = AGENTRULES`.
  - `metadata["scope"]`, `metadata["priority"]` populated from front-matter.
- `emit(bundle)`: serialize directives back to markdown for round-tripping.

### 4.4 Wire directives into the planner

In the orchestrator's prompt assembly, prepend directives **in priority order** before the system prompt. Directives must never override the security interlock — they are advisory.

### Acceptance Criteria — Phase 4

- [ ] `ClaudeSkillFormatAdapter` ingests and emits correctly; digest is stable.
- [ ] `ClaudeClient` attaches skills on requests and passes an integration test.
- [ ] `AgentRulesFormatAdapter` parses at least two file formats (markdown with front-matter, plain markdown).
- [ ] Directives appear in the assembled prompt in the correct order.
- [ ] Unit tests cover round-trip ingest → emit → ingest for Skills and directives.

---

## Phase 5 — Harden the Security Layer

**Goal:** Close the gaps identified in review so that autonomous action is trustworthy.

### 5.1 Enforce schema validation at the interlock

Move the argument-validation call **into `SecurityInterlock`** so no code path can bypass it. Validation failure = denied execution + audit entry.

### 5.2 Persist the audit chain

- Implement `PersistentAuditLogManager` using a write-ahead log or a Room table.
- On startup, verify the chain; if broken, refuse to load tools and surface a clear UI error.
- Add a rolling export (encrypted JSONL) for user-visible transparency.

### 5.3 Protect the risk registry

- Make `ToolRiskRegistry` **append-only** after initialization.
- Expose risk changes only through an explicit admin API that requires user confirmation.
- Log every risk change to the audit trail.

### 5.4 Wire the rate limiter

Attach `TokenBucketRateLimiter` to the `InvocationBridge` so remote MCP calls and model calls are throttled per policy.

### Acceptance Criteria — Phase 5

- [ ] Schema validation runs inside `SecurityInterlock`.
- [ ] Audit chain persists across restarts and verifies on startup.
- [ ] `ToolRiskRegistry` cannot be mutated silently.
- [ ] Rate limiter is active on outbound calls.
- [ ] New tests: tamper detection, restart persistence, blocked schema violations, rate-limit enforcement.

---

## Cross-Cutting Requirements

Apply these to **every** phase:

- **No engine dependency additions.** CI must keep `:core-engine` at zero runtime deps.
- **Ports only.** The engine sees `ToolDefinition` and `ResourceBundle`; it never imports an adapter.
- **Composition over inheritance.** The translator **has** adapters. No base class hierarchies for adapters.
- **Fail-safe defaults.** Unknown source → `L3` risk. Unknown argument → deny and audit.
- **Audit every security-relevant action.** Approval decisions, schema failures, risk changes, tool dispatches.
- **Documentation sync.** Update `docs/` in the same PR that changes behavior. Keep `file_index.md` current.
- **No stub shipping.** If a tool or limiter is still a stub, it must not be reachable from the default tool set.

---

## Definition of Done — Harness Goal

The project can be called a **harness** when all of the following are true:

- [ ] A model can call a **native tool**, an **OpenAI-declared tool**, and an **MCP-provided tool** in the same session.
- [ ] Claude Skills can be attached to a request and the model can use them end-to-end.
- [ ] Directives (AGENTS.md / rule files) are loaded and honored by the planner.
- [ ] Every tool call passes through the security interlock with schema validation, risk gating, and hash-chained audit.
- [ ] The registry can be populated at runtime from at least two external sources without recompiling.
- [ ] `:core-engine` remains dependency-free and port-driven.
- [ ] Docs, tests, and CI reflect all of the above.

---

## Suggested PR Sequence

| PR | Contents | Unlocks |
| :--- | :--- | :--- |
| **PR-1** | Phase 1 — `ToolDefinition`, `TransportHint`, `ResourceBundle`, `NativeToolAdapter`, registry extension | Canonical resource model |
| **PR-2** | Phase 2 — OpenAI `tools` payload, response parsing, argument validation, tool-role messages | Structured tool use on all OpenAI-compatible providers |
| **PR-3** | Phase 3a — `ResourceFormatAdapter`, `ResourceTranslator`, `RiskAnnotator` | Translation layer |
| **PR-4** | Phase 3b — `McpTransportClient`, `McpFormatAdapter`, `ResourceIngress`, `InvocationBridge` | External tool ecosystem |
| **PR-5** | Phase 4 — `ClaudeSkillFormatAdapter`, `ClaudeClient`, `AgentRulesFormatAdapter` | Skills and directives |
| **PR-6** | Phase 5 — security hardening (validation, persistence, registry lock, rate limiter) | Trustworthy autonomy |

Each PR must include tests, doc updates, and a green CI run before merge.