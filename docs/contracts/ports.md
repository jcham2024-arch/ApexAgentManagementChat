# Port Contract Inventory (D4)

Inventory of every port in `engine-core/.../port` and its supporting model records in `engine-core/.../port/model`. Each row lists the port, its package, current method signatures, existing implementations, observed limitations, and the proposed change classification (unchanged / additive / breaking). Any port marked **breaking** requires an ADR before a module spec that touches it is approved.

Per ADR-008, do not open two module specs that modify the same port. Resolve the port change via ADR first, then spec the modules.

## Port contracts

| # | Port | Package | Current method signatures | Implementations today | Observed limitations | Change classification |
|---|------|---------|---------------------------|----------------------|----------------------|-----------------------|
| 1 | `ApprovalPort` | `com.apex.engine.core.port` | `ApprovalDecision request(ApprovalRequest)` | `engine-cli` (terminal, S2.6); `adapter-spring-ai` (SSE, S3) | Synchronous only; event-driven approval is additive (S3) | unchanged |
| 2 | `AuditPort` | `com.apex.engine.core.port` | `AuditEntry append(String, Instant)`, `append(String, Instant, String, String)`, `List<AuditEntry> all()`, `boolean verifyChain()` | `InMemoryAuditLog` (in-memory, S3.3); file-backed planned (S6) | In-memory only — no durable persistence (gap A1) | additive (durable impl via ADR-001) |
| 3 | `ChatEventSink` | `com.apex.engine.core.port` | `void emit(String, long, Instant)`; `NOOP` default | adapters (apex-chat-core); `NOOP` default | Non-blocking contract only; no delivery guarantee | unchanged |
| 4 | `ChatModelPort` | `com.apex.engine.core.port` | `ChatResponse chat(ChatRequest)`; `default void stream(ChatRequest, CancellationToken, Consumer<ChatChunk>)` | `engine-llm-http` (S2.4); `adapter-spring-ai` (planned) | Streaming default emits single terminal chunk; real streaming is additive | unchanged |
| 5 | `CheckpointPort` | `com.apex.engine.core.port` | `void saveCheckpoint(String, int, String)`, `Optional<CheckpointRecord> restoreCheckpoint(String)`, `void deleteCheckpoint(String)` | planned via `engine-store-sqlite` | Not yet implemented | unchanged (impl pending) |
| 6 | `DeploymentRouter` | `com.apex.engine.core.port` | *(record-based)* | `engine-eval` (S2) | Narrow deployment scope | unchanged |
| 7 | `EvaluationProbe` | `com.apex.engine.core.port` | *(record-based)* | `engine-eval` (S2) | Narrow probe scope | unchanged |
| 8 | `GatePlacementPolicy` | `com.apex.engine.core.port` | `boolean gateRequired(Action, int, Plan)`; `default RiskTier tierOf(Action)` | adapters | Risk-tier derivation only; no dynamic policy reload | unchanged |
| 9 | `MetricsPort` | `com.apex.engine.core.port` | *(record-based counter/histogram)* | `engine-metrics` (S1) | No-op where unimplemented | unchanged |
| 10 | `PlannerPort` | `com.apex.engine.core.port` | *(A* planner contract)* | `engine-planner` (AStarPlanner, S2.2) | Deterministic; sequence tie-breaker | unchanged |
| 11 | `RuntimePolicy` | `com.apex.engine.core.port` | `PolicyDecision decide(Plan)` | `PolicyEngine.permissive()` / `denying(String)` | Fail-open default is permissive by explicit opt-in | unchanged |
| 12 | `SecretsPort` | `com.apex.engine.core.port` | `String get(SecretRef)` | environment variables (S3); `adapter-spring-vault` (S3+) | HS256/JWT concerns live in apex-auth, not here | unchanged |
| 13 | `SkillResolver` | `com.apex.engine.core.port` | *(record-based)* | `engine-eval` (S2) | Narrow eval scope | unchanged |
| 14 | `SkillResolver` | `com.apex.engine.core.port` | *(record-based)* | `engine-eval` (S2) | duplicate entry; see #13 | unchanged |
| 15 | `StoragePort` | `com.apex.engine.core.port` | `void appendStep(StepLogEntry)`, `List<StepLogEntry> stepsFor(TenantId, String)`, `void clear(TenantId, String)` | `engine-store-sqlite` (S2.5) | Already tenant-scoped | unchanged |
| 16 | `TelemetryPort` | `com.apex.engine.core.port` | *(record-based span)* | no-op (S2) | No-op where unimplemented | unchanged |
| 17 | `ToolExecutorPort` | `com.apex.engine.core.port` | `ToolResult execute(ToolCall)` | `engine-tools-impl` (planned v1) | No implementations yet (gap A7) | unchanged (impl pending) |
| 18 | `ToolGatewayPort` | `com.apex.engine.core.port` | `GatewayDecision execute(AuthorizationRequest)` | planned via `engine-tools` + `ToolPolicy` + `ApprovalPort` | Fail-closed default-deny | unchanged |
| 19 | `ToolPolicy` | `com.apex.engine.core.port` | *(default-deny policy)* | planned | Not yet implemented | unchanged |
| 20 | `VectorStorePort` | `com.apex.engine.core.port` | *(record-based)* | `engine-store-sqlite` (S4) | Future (S4) | unchanged |
| 21 | `WorkspacePort` | `com.apex.engine.core.port` | *(record-based)* | `engine-store-sqlite` (S2) | Narrow workspace scope | unchanged |

> Note: rows #13 and #14 both reference `SkillResolver` — this reflects a duplicate listing in `PORT-CATALOGUE.md` and should be collapsed to one row in a follow-up. Not a port defect.

## Supporting model records (port/model)

| Record | Package | Purpose | Tier 0 safe? |
|--------|---------|---------|--------------|
| `ApprovalRequest` / `ApprovalDecision` | `com.apex.engine.core.port.model` | HITL approval request/outcome | Yes |
| `AuditEntry` | `com.apex.engine.core.port.model` | Hash-chained audit entry | Yes (may gain `tenantId` per ADR-006 — additive) |
| `AuthorizationRequest` / `GatewayDecision` | `com.apex.engine.core.port.model` | Tool gateway authorization + decision | Yes |
| `CheckpointRecord` | `com.apex.engine.core.port.model` | Plan-execution checkpoint snapshot | Yes |
| `ChatRequest` / `ChatResponse` / `ChatChunk` | `com.apex.engine.core.port.model` | Chat request/response/streaming chunk | Yes |
| `RiskTier` | `com.apex.engine.core.port.model` | Action risk tier (L0..) | Yes |
| `SecretRef` | `com.apex.engine.core.port.model` | Credential reference | Yes |
| `StepLogEntry` | `com.apex.engine.core.port.model` | Persisted step log entry | Yes |
| `ToolCall` / `ToolResult` / `ToolId` | `com.apex.engine.core.port.model` | Tool invocation + result | Yes |

## Classification summary

- **Breaking:** none currently proposed.
- **Additive:** `AuditPort` durable implementation (ADR-001); potential `AuditEntry.tenantId` field (ADR-006); `stream()` real streaming (ADR-004).
- **Unchanged:** all other ports listed above.

Any future breaking change to a port requires an ADR per ADR-008 and must not be undertaken by two module specs concurrently.
