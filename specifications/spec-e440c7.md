---
bundle: f1-model-amendments
profile: 11-amendments
target_branch: main
file_count: 1
file_index:
  - docs/apex-chat-spec/11-amendments.md
---

## Agent Instructions

Read this bundle in full. `file_count` is 1. Write the single file
exactly as shown. Do not modify any file in `docs/apex-chat-spec/` other
than creating this new `11-amendments.md`. Do not touch `ARCHITECTURE.md`
or `README.md` in this bundle — that reference update is a later commit.
Commit as `docs(spec): add amendments — tenant, cost, telemetry, prune,
projection, invariants`. Push and report.

## Files

**docs/apex-chat-spec/11-amendments.md**
```markdown
# Apex Chat — Specification Amendments (File 11)

**Series:** Apex Chat Implementation Specification (11 parts)
**This file:** Amendments that resolve gaps and prune duplication in
Parts 1–10. Read after Part 10.
**Depends on:** Parts 1–10
**Used by:** all runtime implementation

Where this file conflicts with Parts 1–10, this file wins. Where this
file is silent, Parts 1–10 remain authoritative.

---

## 1. Tenant scoping (new §4.9)

Part 1 does not define a tenant type, and no aggregate carries a tenant
identity. For a multi-tenant platform this is a defect. Amend as follows.

### 1.1 New type — `com.apex.chat.common.identity`

```java
public record TenantId(String value) {
    public TenantId {
        Objects.requireNonNull(value, "TenantId");
        if (value.isBlank()) throw new IllegalArgumentException("TenantId blank");
    }
}
```

### 1.2 Every aggregate root carries `tenantId`

| Aggregate | Add field |
|---|---|
| `Chat` (via `ChatIdentity`, §4) | `tenantId: TenantId` |
| `Agent` | `tenantId: TenantId` |
| `AgentRun` | `tenantId: TenantId` |
| `ChatWorkspace` | `tenantId: TenantId` |
| `ChatMemory` | `tenantId: TenantId` |
| `ChatArtifacts` | `tenantId: TenantId` |
| `ChatResources` | `tenantId: TenantId` |

### 1.3 Store ports are tenant-scoped

Every method on `MemoryStore`, `FileStore`, `ArtifactRegistry`,
`ResourceLinkRegistry`, `ToolRegistry`, `SkillRegistry`, `PluginRegistry`
takes `TenantId tenant` as its first parameter, or operates on a
reference that already carries one. Cross-tenant reads are impossible
by construction, not by convention.

### 1.4 ArchUnit rule

Add to §2's ArchUnit suite:

> No type in `core/chat`, `tools`, or `agent` may hold or return a
> reference to another aggregate without a `TenantId` on the same
> instance.

---

## 2. Prune — fold, delete, move, decompose

Part 1 §2.6 says "records for value objects." Part 6 has an 18-field
`Chat`. Part 5 has a `ChatSession` that duplicates `Chat`'s lifecycle.
Part 8 has an `AgentTrace` that duplicates `AgentRun.steps`. These are
internal redundancies, not borrowed from any other project. Prune them.

### 2.1 Fold `ChatSession` into `Chat`

`ChatSession` has four fields: `sessionId`, `status`, `createdAt`,
`metadata`. Every one is subsumed:

- `sessionId` → already `Chat.chatId`
- `status` → becomes `Chat.lifecycle: ChatLifecycle` (§4)
- `createdAt` → becomes a field on `ChatIdentity`
- `metadata` → already `ChatConfiguration.metadata`

**Action:** delete `com.apex.chat.core.session.ChatSession`. Keep
`SessionStatus` as an enum; it now types `Chat.lifecycle.status`.

### 2.2 Delete `AgentTrace` and `AgentSpan`

`AgentRun.steps: List<AgentStep>` is the record of what happened.
`AgentEvent` (in Part 8) is the live stream of what is happening.
`AgentTrace` is a third parallel structure with timing spans that
duplicate the timestamps already on `AgentStep` and `AgentEvent`.

**Action:** delete `com.apex.chat.agent.trace.AgentTrace` and
`AgentSpan`. Keep `AgentEvent` and `SpanContext`. If OTel-style spans
are needed, `runtime-agent` projects them from `AgentEvent` at emit
time. The domain model does not store spans.

### 2.3 Delete `ModelTelemetryHistory`

`com.apex.chat.core.model.ModelTelemetryHistory` is
`{ entries: List<GenerationTelemetry>, windowSize: int }`. It is a
rolling buffer, not a domain type. Aggregation is a runtime concern.

**Action:** delete it. If a runtime needs telemetry aggregation, it
maintains its own buffer keyed by `TypedId<Chat>` and exposes it via a
runtime-only metrics port, not through Layer 1.

### 2.4 Move `InteractionEvent` to the projection layer (§5)

`com.apex.chat.core.ui.InteractionEvent` (11 variants: `StreamingToken`,
`ToolInProgress`, `AwaitingApproval`, etc.) is a UI projection of chat
and agent events, not a domain type. It has no place in a POJO library
that claims "no behavior."

**Action:** remove `InteractionEvent` from `core/ui`. It is redefined
as a client-facing projection in §5 (Event Projection) of this file.
`InteractionState` and `InteractionPrompt` stay in `core/ui` — those
are state, not events.

### 2.5 Delete the duplicate `Message` hierarchy

Part 3 defines `Message` with six sealed variants
(`SystemMessage`, `DeveloperMessage`, `UserMessage`, `AssistantMessage`,
`ToolMessage`, `FunctionMessage`). The retired P1 kernel bundle defined
`DomainMessage` with four. Part 3's version wins. Do not import the P1
version anywhere.

### 2.6 Delete the duplicate event hierarchy

Part 6 defines `ChatEvent` (~30 variants), Part 8 defines `AgentEvent`
(~15 variants). The retired P1 kernel bundle defined `AgentStreamEvent`
(9 variants), and the retired `implementation_plan.md` defined
`ChatLifecycleEvent` + `ChatEventType` enum. Both retired.

The POJO library has two event hierarchies because they have two
scopes: chat-level (`ChatEvent`) and agent-level (`AgentEvent`). Both
stay. `AgentStreamEvent` becomes a projection of both, defined in §5 of
this amendment, and lives in `runtime-server`, not Layer 1.

### 2.7 Decompose `Chat` — see §3

### 2.8 Prune `ModelLimits.rateLimitTier`

`ModelLimits.rateLimitTier: @Nullable String` is a dangling reference to
`RateLimitTier.name`. Replace with
`@Nullable TypedId<RateLimitTier> rateLimitTierRef` and require
resolution through the model registry.

### 2.9 Keep list — types that are not redundant

These stay as specified, for the stated reason:

| Type | Why it survives pruning |
|---|---|
| `ConversationTurn` vs `AgentStep` | Different scopes: turn = chat-level participant utterance with its activity; step = agent-level think-decide-act cycle |
| `ChatEvent` vs `AgentEvent` | Different scopes: chat lifecycle/membership vs agent execution |
| `Artifact` vs `ChatResource` vs `FileEntry` | Different concepts: produced named output vs added external resource vs raw workspace file |
| `MemoryEntry` / `MemoryHit` / `WorkingMemory` | Memory subsystem has internal structure that is not redundant |
| `ChatMutation` | Audit surface for all state transitions |
| `AgentAction` | Six variants map exactly to what an agent can do |
| `ChatProfile` / `ThreadContextMetrics` | Already derived on demand, correctly |

---

## 3. Decompose `Chat` (amends §5.3, §6.1)

Part 6's `Chat` has 18 record components. That is a God object by Part
1 §2.6's own definition. Decompose into five sub-aggregates composed by
`Chat`.

### 3.1 New types

```java
// core/chat/identity
public record ChatIdentity(
    TenantId tenantId,
    TypedId<Chat> chatId,
    Instant createdAt,
    PlatformProfileRef platformProfileRef
) {}

// core/chat/lifecycle
public record ChatLifecycle(
    SessionStatus status,
    ChatInterface iface,
    PromptContext promptContext
) {}

// core/chat/membership
public record ChatMembership(
    ChatRoster roster,
    Map<ChatParticipantRef, OpaqueRef> participantBindings,
    List<ChatCheckpoint> checkpoints
) {}

// core/chat/storage
public record ChatStorage(
    ChatMemory memory,
    ChatWorkspace workspace,
    ChatArtifacts artifacts,
    ChatResources resources
) {}

// core/chat/execution
public record ChatExecution(
    SandboxSpec sandboxSpec,
    Map<CapabilityKind, CapabilityHandle> capabilityHandles
) {}

// core/chat/journal
public record ChatJournal(
    List<ChatEvent> eventLog,
    List<ChatMutation> mutationLog
) {}
```

### 3.2 New `Chat`

```java
public record Chat(
    ChatIdentity identity,
    ChatConfiguration configuration,
    ChatLifecycle lifecycle,
    ChatMembership membership,
    ChatStorage storage,
    ChatExecution execution,
    ConversationThread thread,
    ChatJournal journal
) {}
```

Eight components. Builder still required (fields 6+), but each sub-aggregate
can be tested independently, and adding a new capability touches one
sub-aggregate instead of eighteen fields.

### 3.3 ArchUnit rule

> No consumer of `Chat` may hold a reference to a field of `Chat`; all
> access goes through the sub-aggregate accessors. This prevents the
> God-object pattern from re-emerging via utility methods.

---

## 4. Add `CostEstimation` and `TelemetryEvent` (new §5.3.1)

Part 3 defines `GenerationTelemetry` (latency, modelVersion,
providerRequestId, cacheStatus, cost) and `TokenUsage` (prompt,
completion, cached, reasoning, total). Neither models cost breakdown
and neither is a chat or agent event. Both are needed for Phase P10
(token economics) and for the observability stack.

### 4.1 Add to `core/model`

```java
public record CostEstimation(
    BigDecimal inputCost,
    BigDecimal outputCost,
    BigDecimal reasoningCost,
    BigDecimal cacheDiscount,
    String currencyCode,          // ISO 4217, required, never null
    TypedId<PricingTier> pricingTierId
) {
    public CostEstimation {
        Objects.requireNonNull(inputCost, "inputCost");
        // all BigDecimal components non-null, non-negative
        // cacheDiscount is 0 or positive; it is subtracted in total()
        if (inputCost.signum() < 0) throw new IllegalArgumentException("inputCost < 0");
        // ... same for others
    }
    public BigDecimal total() {
        return inputCost.add(outputCost).add(reasoningCost).subtract(cacheDiscount);
    }
}
```

Four rules that Part 1’s earlier plans violated:

- **`BigDecimal`, not `Double`.** Money math must be exact.
- **`cacheDiscount` is explicit.** Cached tokens bill at a discount;
  the discount must be visible, not silently folded into `inputCost`.
- **`currencyCode` is required** and non-defaulted. If the platform
  ever bills in EUR, the type does not need to change.
- **`total()` is a method, not a field.** A stored total can drift.

### 4.2 Add to `core/model` as an `AgentEvent` variant

```java
public record TelemetryEvent(
    TypedId<ChatEvent> eventId,
    Instant occurredAt,
    TenantId tenantId,
    TypedId<AgentRun> runId,
    TypedId<ModelDescriptor> modelId,
    int turnIndex,
    int stepIndex,
    TokenUsage usage,
    GenerationTelemetry generation,
    CostEstimation cost
) implements AgentEvent {}
```

- `turnIndex` and `stepIndex` are `int`, not `Integer` — required, not
  nullable.
- `modelId` is `TypedId<ModelDescriptor>`, not `String` — resolved
  against the registry at construction.
- Emission point: after the final `TokenChunk` of a step, before the
  step's `StepFinished` event. The ordering is part of the contract
  (see §6).

### 4.3 Remove `ChatLifecycleEvent` / `ChatEventType` retirement note

The retired `implementation_plan.md` proposed `ChatLifecycleEvent` and a
`ChatEventType` enum. Both are abandoned. `TelemetryEvent` above is the
canonical replacement, and it participates in the `AgentEvent` hierarchy
like every other agent-scoped event.

---

## 5. Event projection (new §6.6)

The runtime’s SSE endpoint emits a projection of `ChatEvent` and
`AgentEvent` onto the wire. The projection is defined here so that the
client contract is stable even as the domain model gains events.

### 5.1 Wire envelope

```java
public sealed interface WireEvent
        permits WireToken, WireThought, WireToolCall, WireToolResult,
                WireAgentSpawned, WireProposal, WireBudget, WireStreamError {
    String messageId();
    Instant timestamp();
    String kind();   // stable string discriminator, e.g. "TOKEN"
}
```

### 5.2 Projection rules

| Domain event | Wire event | Notes |
|---|---|---|
| `StreamingEvent.TextDelta` (from the LLM adapter) | `WireToken` | Pass-through after buffering to ≥8 chars |
| `StreamingEvent.ThinkingDelta` | `WireThought` | Only if client requested `showThinking` |
| `ToolCall` (from `AgentStep.action`) | `WireToolCall` | Includes `callId`, `toolName`, `input` |
| `ToolResult` | `WireToolResult` | Includes `callId`, `output`, `error?`, `durationMs` |
| `AgentEvent.CapabilityResolved` where participant kind is `Agent` | `WireAgentSpawned` | Includes the `AgentProfileView` DTO (defined in a later phase) |
| Sentinel-accepted proposal | `WireProposal` | Includes `proposalId`, `rationale`, `diffViewUrl` |
| `TelemetryEvent` at 80% budget | `WireBudget(warning)` | |
| `TelemetryEvent` at 100% budget | `WireBudget(exhausted)` + stream terminates | |
| Any `AgentEvent` or `ChatEvent` classified as error | `WireStreamError` | Includes `code`, `message` |

### 5.3 Non-projected events

Every other `ChatEvent` or `AgentEvent` variant is **internal**. It is
recorded in `ChatJournal` or `AgentRun.steps` but never streamed to the
client. Examples: `ParticipantPresenceChanged`, `TurnStarted`,
`SpanContext`, `PlanUpdated`.

### 5.4 Client contract

The TypeScript client binds to `WireEvent` only. It does not import
`ChatEvent` or `AgentEvent`. The OpenAPI schema is generated from
`WireEvent`. This is what makes the C1 type-drift gate meaningful:
`WireEvent` is a stable, small surface; `ChatEvent` + `AgentEvent` are
free to evolve.

---

## 6. OpaqueRef registry contract (new §6.7)

Part 6 uses `OpaqueRef` for cross-layer references (e.g.
`ChatMutation.SpawnAgent.specRef: OpaqueRef` points at an
`AgentSpawnSpec` that `core` cannot import). Without a registry
contract, every runtime invents its own resolution semantics.

### 6.1 Registry interface

```java
package com.apex.chat.common.runtime;

public interface OpaqueRefRegistry {
    <T> OpaqueRef register(T value, Class<T> type);
    <T> Optional<T> resolve(OpaqueRef ref, Class<T> type);
    boolean forget(OpaqueRef ref);
}
```

### 6.2 Contract

1. **Registration is idempotent by value identity.** Registering the
   same instance twice returns the same `OpaqueRef`. Registering two
   distinct instances that are `.equals()` returns two refs (identity
   is by reference, not by value).
2. **Type is bound at registration.** Resolving a ref against the wrong
   type returns `Optional.empty()` and logs a warning; it does not
   throw.
3. **Resolution is per-runtime.** The registry is a field on
   `RuntimeBindings` (§5.1 of Part 2). It is not part of `Chat`, not
   part of `Agent`, and not part of any aggregate.
4. **Lifecycle.** When a `Chat` is archived, its registry entries are
   forgotten. When an `AgentRun` terminates, its entries are forgotten.
   There are no long-lived opaque refs.
5. **Failure mode.** Unresolvable refs cause the mutation that
   references them to be rejected with `MutationRejection(code =
   "UNRESOLVABLE_REF", message = ref.id())`. This is a hard failure, not
   a silent skip.

### 6.3 Audit surface

Every registration and every forgotten ref is appended to
`ChatJournal.eventLog` as a synthetic event for audit purposes, tagged
`"registry"`. This ensures the audit log is a complete record of what
the runtime could and could not resolve during a run.

---

## 7. Behavioral invariants (new §13.5)

Part 10 §13 checks structure. It does not specify the tests that types
with derived behavior must pass. Add the following tests as mandatory
for Phase F1.

| Type | Invariant | Test |
|---|---|---|
| `TenantId` | null and blank rejected | parameterized |
| `PermissionGrant` | `allows(x)` is false when `expiresAt < now` | fixture with past/future `Instant` |
| `PermissionGrant` | `allows(x)` is false when `x ∉ capabilities` | fixture |
| `TokenUsage` | `totalTokens == promptTokens + completionTokens + reasoningTokens`; `cachedTokens ⊆ promptTokens` | jqwik property over non-negative ints, with the subset constraint |
| `ModelPricing` | `cost(usage)` returns a `BigDecimal` with scale 6 | fixture per model |
| `CostEstimation` | `total() == inputCost + outputCost + reasoningCost - cacheDiscount` | property test |
| `CostEstimation` | all components non-null, all non-negative except `cacheDiscount` which is 0 or positive | parameterized |
| `AgentBudget` | `exhausted(usage, elapsed)` returns true when any of tokens / cost / wallTime / steps / toolCalls is exceeded | fixture per dimension |
| `ChatMutation` | applying any mutation to a `Chat` whose `lifecycle.status` is `Closed` returns `applied=false` with `MutationRejection(code="CHAT_CLOSED")` | fixture per mutation variant |
| `Chat` decomposition | `chat.identity.tenantId` is non-null for every constructed `Chat` | builder test |
| `OpaqueRefRegistry` | resolving a registered value against the wrong type returns `empty()`; resolving a forgotten ref returns `empty()` | fixture |
| `WireEvent` | the projection of a representative `ChatEvent` + `AgentEvent` stream produces exactly the expected `WireEvent` sequence | fixture stream |

These tests are small, focused, and independent of any runtime
implementation. They live in the module that defines the type
(`apex-chat-common` for `TenantId`, `apex-chat-core` for the rest).

---

## 8. Retirement list

The following types from Parts 1–10 are removed or replaced by this
amendment. Each row names the type, the replacement, and the file to
delete it from.

| Removed | Replaced by | File to edit |
|---|---|---|
| `com.apex.chat.core.session.ChatSession` | `Chat.lifecycle: ChatLifecycle` | Part 4 §5.3 `session` |
| `com.apex.chat.core.session.SessionStatus` (as top-level) | `ChatLifecycle.status` | Part 4 §5.3 `session` |
| `com.apex.chat.agent.trace.AgentTrace` | `AgentRun.steps` + `AgentEvent` | Part 8 §5.5 `trace` |
| `com.apex.chat.agent.trace.AgentSpan` | projected from `AgentEvent` at runtime | Part 8 §5.5 `trace` |
| `com.apex.chat.core.model.ModelTelemetryHistory` | runtime-owned buffer | Part 3 §5.3 `model` |
| `com.apex.chat.core.ui.InteractionEvent` | `WireEvent` (§5) | Part 4 §5.3 `ui` |
| `ModelLimits.rateLimitTier: String` | `TypedId<RateLimitTier>` | Part 3 §5.3 `model` |
| `Chat` (18 fields) | `Chat` (8 fields, decomposed) | Part 6 §5.3 `chat` |

The removed types are **not** to be kept as deprecated aliases. A type
that is deleted is deleted. Clients that referenced them get a compile
error, which is the correct migration signal.

---

## 9. What this amendment does not change

This amendment does not change:

- The module topology (Part 1 §3): `common` → `capability` → `core` →
  `tools` → `agent`, runtime modules downstream.
- The ownership invariants (Part 9 §7): `Chat` owns session-scoped
  state, `Agent` owns run-spec state, `AgentRun` owns per-invocation
  state, external stores own durable state.
- The static/dynamic classification (Part 9 §8): the same types remain
  static or dynamic.
- The OWASP mapping (Part 10 §10): the same types address the same
  risks. `CostEstimation` and `TelemetryEvent` augment `LLM10 Unbounded
  Consumption` — noted in the amended package Javadoc for
  `core/guardrail`.
- The dependency rules (Part 1 §3.1): no new imports are introduced by
  any type defined here.
- The three-tier rule hierarchy and the prompt stacks: those live at
  repo root, not in the spec.

---

## 10. Acceptance criteria for this amendment

Before Phase F2 (engine core) begins, the following must be true in
`docs/apex-chat-spec/`:

1. All 10 removed types listed in §8 are absent from the spec files.
2. `TenantId` is defined in `02-common-and-capability.md` §5.1, and
   every aggregate listed in §1.2 carries it.
3. `CostEstimation` and `TelemetryEvent` are defined in
   `03-core-model-content-message-thinking-prompt.md` §5.3, with the
   `BigDecimal` / `TypedId` / `total()`-as-method constraints.
4. `Chat` is decomposed into the six sub-aggregates in §3.
5. §6.6 (Event projection) and §6.7 (OpaqueRef registry) are added to
   the spec as new numbered sections.
6. §13.5 (Behavioral invariants) is added to Part 10 as a new
   subsection.
7. ArchUnit rules from §1.4 and §3.3 are added to the Part 1 §2
   enforcement suite.

Each criterion maps to one edit in the existing spec files. The edits
are mechanical — the model is not being redesigned, it is being
tightened. When all seven pass, the spec is F1-complete and Phase F2
can begin.

---

**End of amendments.** Parts 1–10 plus this file constitute the
authoritative Layer 1 model.
```

## Commit Plan

1. `docs(spec): add amendments — tenant, cost, telemetry, prune,
   projection, invariants`