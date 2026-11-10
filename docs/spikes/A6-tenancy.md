# Spike A6 — Tenancy

**Status:** Finding  
**Date:** 2026-11-10  
**Outcome:** Promote to spec (context-object + storage partition)  
**Owner:** Engineering team

## Question

What isolation model does `apex-tenant` actually need? (`apex-tenant` is currently a package-only shell.)

## Options compared

| Option | Description | Enforcement point | Audit/approval scoping | Complexity |
|--------|-------------|-------------------|------------------------|------------|
| **A6a. Tenant context object (ThreadLocal-free, passed explicitly)** | `TenantId` carried in an `ExecutionContext` passed through ports; every storage/audit/approval call is tenant-scoped by the caller. | Port method parameters | Every `AuditPort.append` and `StoragePort.stepsFor` takes `TenantId` | Low |
| **A6b. Storage-level partition** | Tenant isolation enforced only at the SQLite/DB layer (row-level security, separate schemas). | Storage adapter | Audit/approval must also be partitioned at their store layer | Medium |
| **A6c. Port-level parameter (implicit ambient tenant)** | Tenant implicitly available via a scoped value/ThreadLocal read at adapter boundary. | Adapter boundary | Risky — easy to forget scoping on audit/approval | Medium (and discouraged by AGENT_RULES §2.5) |

## Required answers

### Is isolation a port-level parameter, a context object, or a storage-level partition?

Recommended: **A6a — context object (`ExecutionContext` carrying `TenantId`)**, passed explicitly through port methods. Storage-level partition (A6b) is the *mechanism* inside the storage adapter, but the tenant scope is *declared* via the context object at the port boundary. Ambient tenant (A6c) is rejected because AGENT_RULES §2.5 permits only one ThreadLocal read at the HTTP filter boundary, copied immediately into `ExecutionContext` — it does not permit ambient tenant propagation through the loop.

### How is tenant scoping enforced on audit entries and approvals?

- Every `AuditPort.append(action, at, traceId, spanId)` call must be made with the tenant context known; the audit entry records `tenantId` (via the existing `AuditEntry` model or an extended field) so entries are tenant-scoped and verifiable per tenant.
- Every `StoragePort` call is already tenant-scoped (`stepsFor(TenantId, String)`). `appendStep(StepLogEntry)` entries carry `TenantId`.
- Approvals (`ApprovalPort`) carry the requesting tenant so gating decisions are tenant-scoped.

### What is the failure mode when a tenant context is absent?

**Fail closed.** If `TenantId` is absent or blank at any port call, the adapter throws `IllegalArgumentException` / `TenantRequiredException` before any write. No entry is written without a tenant. This prevents cross-tenant leakage by omission.

## Recommendation

**Promote A6a (context object carrying `TenantId`, passed explicitly through ports)** with storage-level partition as the adapter mechanism.

Rationale:
- Explicit tenant scope at every port call makes isolation auditable and testable.
- Fail-closed on absent tenant prevents silent cross-tenant writes.
- Compatible with AGENT_RULES §2.5 (one ThreadLocal read at filter boundary → copy into `ExecutionContext`).
- Rejected **A6c** (ambient tenant): violates the single ThreadLocal-read rule and makes scoping easy to forget.
- Rejected **A6b alone**: storage-level partition doesn't scope audit/approval unless they also carry tenant; context object is the unifying declaration.

## Open questions

- Whether `TenantId` should be added to `AuditEntry` model (Tier 0 record change — additive, non-breaking) — defer to ADR-006.
- Multi-tenant query performance and index strategy for the SQLite storage adapter — defer to the storage adapter spec.

## Related

- ADR-006: Tenancy isolation model (to be written, Phase B)
- `StoragePort` already tenant-scoped (`stepsFor(TenantId, ...)`)
- `TenantId` in `apex-chat-domain` (Tier 0 identity)
