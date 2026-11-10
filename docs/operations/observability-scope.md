# Observability Scope Decision

Records the observability scope decision for this cycle.

## Decision

**Observability is deferred to a future cycle.** No metrics, logging, or
tracing infrastructure is added in this cycle beyond what already exists
in `engine-metrics` (Tier 1, no-op where unimplemented).

## Rationale

- The current cycle's priority is spec authoring and implementation of
  the first post-pre-spec module (durable audit persistence), not
  observability instrumentation.
- `MetricsPort` and `TelemetryPort` already exist as Tier 0 ports with
  no-op implementations where unimplemented. These provide the integration
  points for future observability work without requiring Tier 0 changes.
- Observability (metrics, logs, traces) belongs in Tier 1 or Tier 2
  modules, never in Tier 0. Adding it now would require careful dependency
  management to avoid Tier 0 purity violations.

## Deferral Record

| Item | Decision | Reason |
|------|----------|--------|
| Metrics collection | Deferred | Not in cycle scope; `MetricsPort` exists as no-op |
| Distributed tracing | Deferred | Not in cycle scope; `TelemetryPort` exists as no-op |
| Structured logging | Deferred | Application-level concern; not kernel infrastructure |
| Observability dashboards | Deferred | Deployment-specific; not kernel infrastructure |

## Re-enablement Criteria

Observability work should be reconsidered when:
- A spec author requires metrics for their module, or
- The durable audit persistence module is deployed to production and
  operational visibility is needed, or
- A stakeholder explicitly requests observability as a blocking requirement.

When re-enabled, metrics belong in a Tier 1 module (never Tier 0), and the
existing `MetricsPort`/`TelemetryPort` interfaces provide the integration
points.
