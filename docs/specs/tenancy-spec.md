# BUILD-SPEC: Tenancy Isolation (`apex-tenant`)

## 1. Module Identity

| Field | Value |
| :--- | :--- |
| **Module name** | `apex-tenant` (existing Tier 2 module, shell → impl) |
| **Repository location** | `project/apex-platform/apex-tenant/` |
| **Parent aggregator** | `project/apex-platform/pom.xml` |
| **Tier** | **Tier 2** (platform services — external libraries allowed) |
| **Governed by** | ADR-006 (Tenancy isolation model) |
| **Spike input** | `docs/spikes/A6-tenancy.md` |
| **Decision input** | `docs/contracts/decisions/Q13-tenancy-isolation.md` |
| **Spec readiness** | **READY** — Q13 closed, ADR-006 accepted, port classification additive |

## 2. Purpose

Implement tenant isolation in `apex-tenant` (currently a package-only shell).
Every state mutation — audit entries, approvals, step logs — is tenant-scoped.
A missing tenant context fails closed (never resolves to a default tenant).

## 3. Ports Consumed and Provided

### Consumed (Tier 0 contracts)

| Port | Package | Used as |
|------|---------|---------|
| `StoragePort` | `com.apex.engine.core.port` | Tenant-scoped `stepsFor(TenantId, String)` and `appendStep(StepLogEntry)` with `tenantId` on entries |
| `AuditPort` | `com.apex.engine.core.port` | `tenantId` recorded on every `AuditEntry` (additive field) |
| `ApprovalPort` | `com.apex.engine.core.port` | Gating decisions tenant-scoped |

### Provided (Tier 2 implementations)

| Implementation | Purpose |
|---------------|---------|
| `TenantContext` | Request-scoped `TenantId` carrier (ThreadLocal sync + TTL + TaskDecorator async) |
| `TenantScopedStoragePort` | `StoragePort` decorator enforcing `tenantId` on every call |
| `TenantScopedAuditPort` | `AuditPort` decorator enforcing `tenantId` on every entry |

### Port classification

- **`StoragePort`**: **additive** — `appendStep` entries gain `tenantId`; method signature unchanged.
- **`AuditPort`**: **additive** — `AuditEntry` gains `tenantId` field; method signatures unchanged.
- **`ApprovalPort`**: **unchanged** — tenant scope applied at call site, not port signature.
- **No new Tier 0 ports introduced.**

## 4. Public API Surface and Compatibility

### Java API

```java
// New tenant context (Tier 2)
public final class TenantContext {
    public static TenantId get();           // thread-local, sync flows
    public static void set(TenantId tenantId);
    public static void clear();
    public static <T> T callWith(TenantId tenantId, Supplier<T> action); // async-safe via TTL
}

// Tenant-scorted storage decorator (Tier 2)
public final class TenantScopedStoragePort implements StoragePort {
    public TenantScopedStoragePort(StoragePort delegate);
    public void appendStep(StepLogEntry entry);   // enforces tenantId on entry
    public List<StepLogEntry> stepsFor(TenantId tenantId, String sessionId);
    public void clear(TenantId tenantId, String sessionId);
}

// Tenant-scoped audit decorator (Tier 2)
public final class TenantScopedAuditPort implements AuditPort {
    public TenantScopedAuditPort(AuditPort delegate);
    public AuditEntry append(String action, Instant at);
    public AuditEntry append(String action, Instant at, String traceId, String spanId);
    public List<AuditEntry> all();
    public boolean verifyChain();
}
```

### Compatibility statement

- **`StoragePort.appendStep(StepLogEntry)`**: signature unchanged; entries
  now carry `tenantId` (additive field on `StepLogEntry` if not already present).
- **`AuditPort.append(...)`**: signature unchanged; entries now carry
  `tenantId` (additive field on `AuditEntry`).
- **`AuditEntry` record**: gains `tenantId` field (additive, non-breaking;
  existing code constructing `AuditEntry` without `tenantId` needs updating
  or a default).
- **No breaking changes** to any Tier 0 port interface.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `com.apex:engine-core` | Compile | Tier 0 ports and `TenantId` (project dependency). |
| `com.apex.chat:apex-chat-domain` | Compile | `TenantId` identity type (project dependency, Layer 1). |
| `com.apex:apex-audit` | Compile | `AuditPort`, `AuditEntry`, `HashChain` (project dependency). |
| `org.jspecify` | Compile | Null-safety annotations per project standards. |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per project standard. |
| `org.assertj:assertj-core` | Test | Assertions per project standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural enforcement per project standard. |

**No new external dependencies.** All dependencies are project-internal or
already-approved (jspecify, JUnit, AssertJ, ArchUnit).

**Tier 0 purity:** `apex-tenant` is Tier 2 — no Tier 0 module is modified.
`TenantId` already exists in `apex-chat-domain` (Layer 1, pure POJO).

## 6. Implementation Design

### 6.1 Isolation model

**Shared schema with mandatory `tenant_id` filtering** — the default for
scalability. Schema-per-tenant and database-per-tenant are reserved for
high-isolation or compliance-sensitive tenants (not implemented in v1).

### 6.2 Tenant context propagation

- **Synchronous flows:** `ThreadLocal`-based `TenantContext.get()/set()/clear()`.
- **Async flows:** `TransmittableThreadLocal` (TTL) plus a `TaskDecorator`
  so the context survives thread-pool reuse. A plain `ThreadLocal` is
  **forbidden** for async work (loses context on thread-pool reuse).

### 6.3 Enforcement points

| Layer | Enforcement |
|-------|-----------|
| Port boundary | `TenantScopedStoragePort` enforces `tenantId` on every `appendStep`; `TenantScopedAuditPort` enforces `tenantId` on every `append`. |
| Storage layer | `stepsFor(TenantId, String)` already tenant-scoped by signature. |
| Audit entries | Every `AuditEntry` carries `tenantId`; verifiable per tenant. |
| Approvals | `ApprovalPort` gating decisions carry the requesting tenant. |

### 6.4 Fail-closed behavior

A missing or blank `TenantId` at any port call throws
`IllegalArgumentException` / `TenantRequiredException` **before** any write.
No entry is written without a tenant. This prevents cross-tenant leakage by
omission.

### 6.5 `tenantId` on `AuditEntry`

`tenantId` becomes an additive field on `AuditEntry` (Tier 0 record).
This resolves the deferred item from the audit build spec. The durable
audit adapter (`apex-audit-persistence-sqlite`) persists it as a column.

## 7. Determinism Impact

**No impact on Tier 0 determinism.** Tenant isolation is a scoping filter,
not a behavioral change. `PlanLoop` remains deterministic given the same
inputs; tenant context does not affect plan generation or audit ordering.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **Cross-tenant leakage** | Prevented by fail-closed on absent tenant; `tenantId` on every |
| | audit entry and step log; tenant-scoped queries at storage layer. |
| **Async context loss** | Prevented by `TransmittableThreadLocal` + `TaskDecorator`; |
| | plain `ThreadLocal` forbidden for async work. |
| **Default tenant** | Forbidden — missing context is an error, never a default tenant. |

## 9. Audit Impact

Every `AuditEntry` carries `tenantId`. The durable audit adapter persists
it as a column. `verifyChain()` operates per-tenant (filter by `tenantId`
before chain verification) or globally (verify all entries in order — chain
integrity is independent of tenancy).

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **Missing tenant context** | `IllegalArgumentException` / `TenantRequiredException` |
| | thrown before any write; no entry written. |
| **Blank tenantId** | Same as missing — fail closed. |
| **Decorator misconfiguration** | Context fails to start with a clear error. |

Cancellation and timeouts are not tenant-specific; they follow the
underlying port's semantics.

## 11. Test Plan

### 11.1 Unit tests

- `TenantContext.get()`/`set()`/`clear()` round-trip in sync flow.
- `TenantContext.callWith()` propagates context to worker thread (async).
- Plain `ThreadLocal` not used for async (enforced by code review +
  `CorePurityTest`-style check if applicable).
- `TenantScopedStoragePort.appendStep()` rejects entry without `tenantId`.
- `TenantScopedStoragePort.stepsFor()` returns only matching tenant.
- `TenantScopedAuditPort.append()` records `tenantId` on entry.
- Missing `TenantId` throws before any write (fail closed).

### 11.2 Integration tests

- **Pooled thread context:** A tool invocation submitted to a thread pool
  observes the correct `tenantId` on the worker thread (acceptance test
  minimum).
- **No tenant context:** An invocation with no tenant context fails closed
  (acceptance test minimum).
- **Cross-tenant isolation:** An audit entry written under tenant A is not
  visible to tenant B via `stepsFor(tenantB, ...)` (acceptance test minimum).
- **Decorator chaining:** `TenantScopedStoragePort` wraps a real
  `StoragePort` implementation and enforces `tenantId` on every call.

### 11.3 Architectural enforcement

- `CorePurityTest` passes in all Tier 0 modules.
- No Tier 0 module modified; `TenantId` already exists in Layer 1.

### 11.4 Coverage targets

- `TenantContext` sync + async propagation exercised.
- `TenantScopedStoragePort` and `TenantScopedAuditPort` exercised.
- Fail-closed on missing tenant exercised.
- Cross-tenant isolation exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `apex-tenant` builds under `mvn -B -f project/apex/pom.xml verify`.
2. `TenantContext` provides sync (`ThreadLocal`) and async (TTL +
   `TaskDecorator`) propagation.
3. A tool invocation on a pooled thread observes the correct tenant context
   on the worker thread (acceptance test minimum).
4. An invocation with no tenant context fails closed (acceptance test
   minimum).
5. An audit entry written under tenant A is not visible to tenant B
   (acceptance test minimum).
6. `tenantId` is an additive field on `AuditEntry` (non-breaking).
7. `TenantScopedStoragePort` and `TenantScopedAuditPort` enforce `tenantId`
   on every call.
8. `CorePurityTest` passes in all Tier 0 modules (no Tier 0 changes).
9. No plain `ThreadLocal` used for async work.

## 13. Rollback / Disable Strategy

- **Exclude the module:** Applications that do not need tenancy simply do
  not add `apex-tenant` as a dependency.
- **Fallback to unscoped ports:** Applications can use `StoragePort` and
  `AuditPort` directly without the tenant-scoped decorators.
- **No Tier 0 impact:** `TenantId` already exists in Layer 1; adding
  `tenantId` to `AuditEntry` is additive.

## 14. Out of Scope (Explicit)

- Schema-per-tenant or database-per-tenant (reserved for compliance-sensitive
  tenants; not implemented in v1).
- Multi-tenant query performance tuning / index strategy (Q14 non-blocking;
  deferred).
- Tenant onboarding / provisioning (application-layer concern).
- Row-level security at the database level (enforced at port boundary
  instead).

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A6-tenancy.md` |
| Governing ADR | `docs/adr/ADR-006-tenancy-isolation-model.md` |
| Decision note | `docs/contracts/decisions/Q13-tenancy-isolation.md` |
| Port contract inventory | `docs/contracts/ports.md` (`StoragePort`, `AuditPort`, `ApprovalPort` rows) |
| NFR capture | `docs/contracts/nfr.md` §6 |
| Open questions | `docs/contracts/open-questions.md` (Q13 closed) |
| Spec readiness checklist | `docs/specs/README.md` |

**Decision governance:** Q13 (shared schema + `tenant_id`, fail-closed,
async TTL propagation, `tenantId` schema reservation) is closed and linked
above. These decisions constrain this spec as stated in each decision note's
"Constraint on future specs" section.
