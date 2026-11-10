# Decision: Q13 — Tenancy Isolation Model

## Question
Q13: Whether `tenantId` should be added to `AuditEntry` model.

## Chosen value
**Isolation model:** Shared schema with mandatory `tenant_id` filtering, unless a compliance requirement forces schema-per-tenant or database-per-tenant. Shared schema with `tenant_id` is the default for scalability; physical models reserved for high-isolation or compliance-sensitive tenants.
**Tenant context:** A request-scoped `TenantContext` propagated through the call chain. For synchronous flows, a `ThreadLocal`; for async flows, a `TransmittableThreadLocal` (TTL) plus a `TaskDecorator` so the context survives thread-pool reuse.
**Enforcement point:** Tenant scoping enforced at the port boundary and the storage layer, not only in business logic. Every audit entry and approval record carries the tenant identifier.
**Absent context:** Fail closed. A missing tenant context is an error, never a default tenant.
**Audit integration:** `tenantId` becomes an additive field on `AuditEntry`; the schema reserves the column now, and the durable audit adapter persists it. This resolves the deferred item from the audit build spec.

## Approver
Security + data platform + compliance (30-minute decision session).

## Evidence
The isolation model is a compliance decision, not a purely technical one, and the industry pattern is well-established across three tiers: shared table with `tenant_id`, schema-per-tenant, and database-per-tenant, with the shared-table model being the default for scalability and the physical models reserved for high-isolation or compliance-sensitive tenants. The async propagation detail catches most implementations: a plain `ThreadLocal` loses the tenant identifier the moment work crosses a thread pool boundary. The documented fix is either a `TaskDecorator` that copies the context onto the worker thread, or a `TransmittableThreadLocal` that propagates automatically. The fail-closed rule is non-negotiable: a missing tenant context that silently resolves to a default tenant is a cross-tenant data leak.

## Rationale
Shared schema with `tenant_id` filtering is the default for scalability, with physical isolation reserved for compliance-sensitive tenants. Async context propagation via TTL + `TaskDecorator` prevents intermittent cross-tenant leaks on thread-pool reuse. Fail-closed prevents the single most common tenancy bug. `tenantId` as an additive field on `AuditEntry` resolves the audit build spec's deferred item and makes audit entries tenant-scoped and verifiable per tenant.

## Constraint on future specs
- A missing tenant context MUST be treated as an error (fail closed), never resolved to a default tenant — a silent default tenant is a cross-tenant data leak.
- A plain `ThreadLocal` MUST NOT be used for async work; it loses context on thread-pool reuse. Async flows require TTL + `TaskDecorator`.
- Tenant scoping MUST be enforced at the port boundary and the storage layer, not only in business logic.
- `tenantId` MUST be an additive field on `AuditEntry` (non-breaking); the durable audit adapter MUST persist it.
- The isolation model choice (shared schema default) MUST be recorded; schema-per-tenant or database-per-tenant requires a separate compliance-driven decision.

## Link
- Governing ADR: `docs/adr/ADR-006-tenancy-isolation-model.md`
- Build spec: tenancy build spec (readiness gate blocked on Q13; now cleared)
