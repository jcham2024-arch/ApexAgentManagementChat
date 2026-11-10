# ADR-006: Tenancy isolation model

**Status:** Accepted  
**Deciders:** Engineering team  
**Date:** 2026-11-10  
**Supersedes:** none  
**Depends on:** Spike A6 (docs/spikes/A6-tenancy.md)

## Context

`apex-tenant` is currently a package-only shell (only `package-info.java` + `TenantPackageTest`). There is no tenant isolation implementation. Enterprise deployments require tenant-scoped audit entries, approvals, and storage, with fail-closed behavior when a tenant context is absent.

## Decision

**Adopt a context-object tenant model (A6a): `TenantId` carried explicitly in an `ExecutionContext` passed through port methods, with storage-level partitioning as the adapter mechanism.**

- `TenantId` (already in `apex-chat-domain`, Tier 0) is passed as an explicit parameter/field at every port call that reads or writes tenant-scoped data (`StoragePort.appendStep`, `StoragePort.stepsFor`, `AuditPort.append`, `ApprovalPort` gating).
- `StoragePort` is already tenant-scoped (`stepsFor(TenantId, String)`); `appendStep(StepLogEntry)` entries carry `TenantId`.
- Audit entries record `tenantId` (additive, non-breaking field on `AuditEntry`) so entries are tenant-scoped and verifiable per tenant.
- Approvals carry the requesting tenant so gating decisions are tenant-scoped.
- Fail closed: absent or blank `TenantId` throws before any write; no entry is written without a tenant.
- One ThreadLocal read at the HTTP filter boundary (AGENT_RULES §2.5), copied immediately into `ExecutionContext`; no ambient tenant propagation through the loop.

## Alternatives considered

| Option | Verdict | Reason |
|--------|---------|--------|
| Context object + explicit `TenantId` (A6a) | **Accepted** | Explicit, auditable, testable scoping; compatible with the single ThreadLocal-read rule. |
| Storage-level partition only (A6b) | Rejected alone | Does not scope audit/approval unless they also carry tenant; not a unifying declaration. |
| Ambient tenant / ThreadLocal (A6c) | Rejected | Violates AGENT_RULES §2.5; makes scoping easy to forget. |

## Consequences

### Positive
- Tenant isolation auditable at every port call.
- Fail-closed prevents silent cross-tenant writes.
- Compatible with existing `StoragePort` signature and `TenantId` identity type.

### Negative
- Adding `tenantId` to `AuditEntry` is an additive Tier 0 record change (non-breaking) requiring care with serialization/canonical form used before HMAC generation.
- Multi-tenant query performance/index strategy for the SQLite storage adapter deferred to the storage adapter spec.

## Related

- docs/spikes/A6-tenancy.md — the spike finding behind this decision
- `StoragePort` in `engine-core/port` (Tier 0, already tenant-scoped)
- `TenantId` in `apex-chat-domain` (Tier 0)
- `AuditEntry` in `engine-core/port/model` (Tier 0 — may gain `tenantId`)
- docs/contracts/decisions/Q13-tenancy-isolation.md — Q13 decision note (shared schema + tenant_id, fail-closed)
