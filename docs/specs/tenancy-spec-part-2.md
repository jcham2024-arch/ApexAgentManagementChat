# BUILD-SPEC: Tenancy Isolation (`apex-tenant`) — Part 2 of 2

**Source:** docs/specs/tenancy-spec.md (original, chunked per AGENT_RULES §2.13)
**Part:** 2 of 2
**Cross-reference:** See `tenancy-spec-part-1.md` for sections 1-6.

## 8. Security Impact

| Concern | Treatment |
| :--- | :--- |
| **Cross-tenant leakage** | Prevented by fail-closed on absent |
| | tenant; `tenantId` on every audit entry |
| | and step log; tenant-scoped queries at |
| | storage layer. |
| **Async context loss** | Prevented by `TransmittableThreadLocal` |
| | + `TaskDecorator`; plain `ThreadLocal` |
| | forbidden for async work. |
| **Default tenant** | Forbidden — missing context is an error, |
| | never a default tenant. |

## 9. Audit Impact

Every `AuditEntry` carries `tenantId`. The durable audit adapter
persists it as a column. `verifyChain()` operates per-tenant (filter by
`tenantId` before chain verification) or globally (verify all entries
in order — chain integrity is independent of tenancy).

## 10. Failure, Cancellation, and Timeout Semantics

| Scenario | Required Behavior |
| :--- | :--- |
| **Missing tenant context** | `IllegalArgumentException` / |
| | `TenantRequiredException` thrown before |
| | any write; no entry written. |
| **Blank tenantId** | Same as missing — fail closed. |
| **Decorator misconfiguration** | Context fails to start |
| | with a clear error. |

Cancellation and timeouts are not tenant-specific; they follow the
underlying port's semantics.

## 11. Test Plan

### 11.1 Unit tests

- `TenantContext.get()`/`set()`/`clear()` round-trip in |
| sync flow.
- `TenantContext.callWith()` propagates context to |
| worker thread (async).
- Plain `ThreadLocal` not used for async (enforced |
| by code review + `CorePurityTest`-style check if |
| applicable).
- `TenantScopedStoragePort.appendStep()` rejects |
| entry without `tenantId`.
- `TenantScopedStoragePort.stepsFor()` returns |
| only matching tenant.
- `TenantScopedAuditPort.append()` records |
| `tenantId` on entry.
- Missing `TenantId` throws before any write |
| (fail closed).

### 11.2 Integration tests

- **Pooled thread context:** A tool invocation |
| submitted to a thread pool observes the correct |
| `tenantId` on the worker thread (acceptance test |
| minimum).
- **No tenant context:** An invocation with no |
| tenant context fails closed (acceptance test |
| minimum).
- **Cross-tenant isolation:** An audit entry |
| written under tenant A is not visible to tenant |
| B via `stepsFor(tenantB, ...)` (acceptance test |
| minimum).
- **Decorator chaining:** `TenantScopedStoragePort` |
| wraps a real `StoragePort` implementation and |
| enforces `tenantId` on every call.

### 11.3 Architectural enforcement

- `CorePurityTest` passes in all Tier 0 |
| modules.
- No Tier 0 module modified; `TenantId` already |
| exists in Layer 1.

### 11.4 Coverage targets

- `TenantContext` sync + async propagation |
| exercised.
- `TenantScopedStoragePort` and |
| `TenantScopedAuditPort` exercised.
- Fail-closed on missing tenant exercised.
- Cross-tenant isolation exercised.

## 12. Acceptance Criteria

The build is acceptable when all of the following are demonstrably true:

1. `apex-tenant` builds under `mvn -B -f |
| project/apex/pom.xml verify`.
2. `TenantContext` provides sync (`ThreadLocal`) |
| and async (TTL + `TaskDecorator`) propagation.
3. A tool invocation on a pooled thread observes |
| the correct tenant context on the worker |
| thread (acceptance test minimum).
4. An invocation with no tenant context fails |
| closed (acceptance test minimum).
5. An audit entry written under tenant A is not |
| visible to tenant B (acceptance test minimum).
6. `tenantId` is an additive field on |
| `AuditEntry` (non-breaking).
7. `TenantScopedStoragePort` and |
| `TenantScopedAuditPort` enforce `tenantId` on |
| every call.
8. `CorePurityTest` passes in all Tier 0 |
| modules (no Tier 0 changes).
9. No plain `ThreadLocal` used for async work.

## 13. Rollback / Disable Strategy

- **Exclude the module:** Applications that do |
| not need tenancy simply do not add |
| `apex-tenant` as a dependency.
- **Fallback to unscoped ports:** Applications |
| can use `StoragePort` and `AuditPort` directly |
| without the tenant-scoped decorators.
- **No Tier 0 impact:** `TenantId` already |
| exists in Layer 1; adding `tenantId` to |
| `AuditEntry` is additive.

## 14. Out of Scope (Explicit)

- Schema-per-tenant or database-per-tenant |
| (reserved for compliance-sensitive tenants; |
| not implemented in v1).
- Multi-tenant query performance tuning / |
| index strategy (Q14 non-blocking; deferred).
- Tenant onboarding / provisioning |
| (application-layer concern).
- Row-level security at the database level |
| (enforced at port boundary instead).

## 15. Governance References

| Artifact | Path |
| :--- | :--- |
| Spike finding | `docs/spikes/A6-tenancy.md` |
| Governing ADR | `docs/adr/ADR-006-tenancy-isolation-model.md` |
| Decision note | `docs/contracts/decisions/Q13-tenancy-isolation.md` |
| Port contract inventory | `docs/contracts/ports.md` |
| | (`StoragePort`, `AuditPort`, `ApprovalPort` |
| | rows) |
| NFR capture | `docs/contracts/nfr.md` §6 |
| Open questions | `docs/contracts/open-questions.md` |
| | (Q13 closed) |
| Spec readiness checklist | `docs/specs/README.md` |

**Decision governance:** Q13 (shared schema + |
| `tenant_id`, fail-closed, async TTL |
| propagation, `tenantId` schema reservation) |
| is closed and linked above. These decisions |
| constrain this spec as stated in each |
| decision note's "Constraint on future specs" |
| section.
