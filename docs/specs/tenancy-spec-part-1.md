# BUILD-SPEC: Tenancy Isolation (`apex-tenant`) — Part 1 of 2

**Source:** docs/specs/tenancy-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 1 of 2
**Cross-reference:** See `tenancy-spec-part-2.md` for sections 7-15.

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
| **Spec readiness** | **READY** — Q13 closed, ADR-006 accepted, port classification |
| | additive |

## 2. Purpose

Implement tenant isolation in `apex-tenant` (currently a package-only
shell). Every state mutation — audit entries, approvals, step logs —
is tenant-scoped. A missing tenant context fails closed (never resolves
to a default tenant).

## 3. Ports Consumed and Provided

### Consumed (Tier 0 contracts)

| Port | Package | Used as |
|------|---------|---------|
| `StoragePort` | `com.apex.engine.core.port` | Tenant-scoped |
| | | `stepsFor(TenantId, String)` |
| | | and `appendStep(StepLogEntry)` |
| | | with `tenantId` on entries |
| `AuditPort` | `com.apex.engine.core.port` | `tenantId` recorded on every |
| | | `AuditEntry` (additive field) |
| `ApprovalPort` | `com.apex.engine.core.port` | Gating decisions tenant-scoped |

### Provided (Tier 2 implementations)

| Implementation | Purpose |
|---------------|---------|
| `TenantContext` | Request-scoped `TenantId` carrier |
| | (`ThreadLocal` sync + TTL + |
| | `TaskDecorator` async) |
| `TenantScopedStoragePort` | `StoragePort` decorator enforcing |
| | `tenantId` on every call |
| `TenantScopedAuditPort` | `AuditPort` decorator enforcing |
| | `tenantId` on every entry |

### Port classification

- **`StoragePort`**: **additive** — `appendStep` entries gain |
| `tenantId`; method signature unchanged.
- **`AuditPort`**: **additive** — `AuditEntry` gains `tenantId` |
| field; method signatures unchanged.
- **`ApprovalPort`**: **unchanged** — tenant scope applied at call |
| site, not port signature.
- **No new Tier 0 ports introduced.**

## 4. Public API Surface and Compatibility

### Java API

```java
// New tenant context (Tier 2)
public final class TenantContext {
    public static TenantId get();           // thread-local, sync flows
    public static void set(TenantId tenantId);
    public static void clear();
    public static <T> T callWith(TenantId tenantId,
                                 Supplier<T> action); // async-safe |
    | via TTL
}

// Tenant-scorted storage decorator (Tier 2)
public final class TenantScopedStoragePort implements StoragePort {
    public TenantScopedStoragePort(StoragePort delegate);
    public void appendStep(StepLogEntry entry);   // enforces |
    | tenantId on entry
    public List<StepLogEntry> stepsFor(TenantId tenantId,
                                       String sessionId);
    public void clear(TenantId tenantId, String sessionId);
}

// Tenant-scoped audit decorator (Tier 2)
public final class TenantScopedAuditPort implements AuditPort {
    public TenantScopedAuditPort(AuditPort delegate);
    public AuditEntry append(String action, Instant at);
    public AuditEntry append(String action, Instant at,
                             String traceId, String spanId);
    public List<AuditEntry> all();
    public boolean verifyChain();
}
```

### Compatibility statement

- **`StoragePort.appendStep(StepLogEntry)`**: signature unchanged; |
| entries now carry `tenantId` (additive field on `StepLogEntry` if |
| not already present).
- **`AuditPort.append(...)`**: signature unchanged; entries now |
| carry `tenantId` (additive field on `AuditEntry`).
- **`AuditEntry` record**: gains `tenantId` field (additive, |
| non-breaking; existing code constructing `AuditEntry` without |
| `tenantId` needs updating or a default).
- **No breaking changes** to any Tier 0 port interface.

## 5. Dependency Ceiling and Justification

| Dependency | Scope | Justification |
| :--- | :--- | :--- |
| `com.apex:engine-core` | Compile | Tier 0 ports and |
| | | `TenantId` (project dependency). |
| `com.apex.chat:apex-chat-domain` | Compile | `TenantId` identity |
| | | type (project dependency, Layer 1). |
| `com.apex:apex-audit` | Compile | `AuditPort`, |
| | | `AuditEntry`, `HashChain` |
| | | (project dependency). |
| `org.jspecify` | Compile | Null-safety annotations per |
| | | project standards. |
| `org.junit.jupiter:junit-jupiter` | Test | Test framework per |
| | | project standard. |
| `org.assertj:assertj-core` | Test | Assertions per |
| | | project standard. |
| `com.tngtech.archunit:archunit-junit5` | Test | Architectural |
| | | enforcement per project |
| | | standard. |

**No new external dependencies.** All dependencies are project-internal or
already-approved (jspecify, JUnit, AssertJ, ArchUnit).

**Tier 0 purity:** `apex-tenant` is Tier 2 — no Tier 0 module is
modified. `TenantId` already exists in `apex-chat-domain` (Layer 1,
pure POJO).

## 6. Implementation Design

### 6.1 Isolation model

**Shared schema with mandatory `tenant_id` filtering** — the default for
scalability. Schema-per-tenant and database-per-tenant are reserved for
high-isolation or compliance-sensitive tenants (not implemented in v1).

### 6.2 Tenant context propagation

- **Synchronous flows:** `ThreadLocal`-based |
| `TenantContext.get()/set()/clear()`.
- **Async flows:** `TransmittableThreadLocal` (TTL) plus a |
| `TaskDecorator` so the context survives thread-pool reuse. A plain |
| `ThreadLocal` is **forbidden** for async work (loses context on |
| thread-pool reuse).

### 6.3 Enforcement points

| Layer | Enforcement |
|-------|-----------|
| Port boundary | `TenantScopedStoragePort` enforces `tenantId` on every |
| | `appendStep`; `TenantScopedAuditPort` enforces `tenantId` |
| | on every `append`. |
| Storage layer | `stepsFor(TenantId, String)` already |
| | tenant-scoped by signature. |
| Audit entries | Every `AuditEntry` carries `tenantId`; |
| | verifiable per tenant. |
| Approvals | `ApprovalPort` gating decisions carry |
| | the requesting tenant. |

### 6.4 Fail-closed behavior

A missing or blank `TenantId` at any port call throws |
`IllegalArgumentException` / `TenantRequiredException` **before** any |
write. No entry is written without a tenant. This prevents |
cross-tenant leakage by omission.

### 6.5 `tenantId` on `AuditEntry`

`tenantId` becomes an additive field on `AuditEntry` (Tier 0 record).
This resolves the deferred item from the audit build spec. The
durable audit adapter (`apex-audit-persistence-sqlite`) persists it
as a column.

## 7. Determinism Impact

**No impact on Tier 0 determinism.** Tenant isolation is a scoping |
filter, not a behavioral change. `PlanLoop` remains deterministic |
given the same inputs; tenant context does not affect plan |
generation or audit ordering.
