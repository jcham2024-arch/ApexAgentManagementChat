# Spike A3 — Spring Boot Starter

**Status:** Finding  
**Date:** 2026-11-10  
**Outcome:** Promote to spec (as a Spring-free kernel embedding surface)  
**Owner:** Engineering team

## Question

What is the minimum auto-configuration surface that lets an existing Spring application embed the kernel?

## Constraint

Tier 0 (engine-core, engine-planner, engine-tools, engine-store, engine-json, engine-memory) must remain free of Spring and all enterprise dependencies per AGENT_RULES §2. Spring types must **never** leak into Tier 0. Therefore a "Spring Boot starter" cannot be a Tier 0 module; it lives in Tier 2 (a platform adapter) and depends on Tier 0 only through ports.

## Options compared

| Option | Description | Spring in Tier 0? | Kernel lifecycle control | Caller-supplied adapter? | Complexity |
|--------|-------------|-------------------|--------------------------|--------------------------|------------|
| **A3a. Spring Boot `@Configuration` starter (Tier 2)** | `@Configuration` class exposes `PlanLoop`, `AuditPort`, `ChatModelPort`, etc. as Spring beans. Kernel constructed from ports; Spring wires adapters. | No | Spring manages `PlanLoop` start/stop via `SmartLifecycle` | Yes — caller provides adapter beans | Medium |
| **A3b. Spring AI starter wrapper** | Starter that pulls in Spring AI as a transitive dep and wires `ChatModelPort` to Spring AI. | No (Spring AI in adapter only) | Spring AI manages model lifecycle | Yes | Medium |
| **A3c. Programmatic kernel + Spring adapter** | No auto-config; user builds kernel manually and passes adapters. Spring only provides the adapter implementations. | No | Caller manages lifecycle | Yes | Low (but poor DX) |

## Required answers

### Which ports become beans?

The Tier 2 starter exposes these as Spring beans: `PlanLoop` (lifecycle), `AuditPort`, `ChatModelPort`, `ToolExecutorPort`, `StoragePort`, `CheckpointPort`, `RuntimePolicy`, `GatePlacementPolicy`, `SecretsPort`, `MetricsPort`, `TelemetryPort`. Each has a default Tier 1 implementation plus an overridable Tier 2 bean.

### How is PlanLoop lifecycle managed?

`PlanLoop` implements Spring's `SmartLifecycle` (or a thin adapter does). `start()` begins the loop with a `CancellationToken`; `stop()` cancels the token and waits for in-flight steps to drain. The `ApplicationContext` shutdown hook triggers `stop()`.

### How does auth integrate as a filter?

A Spring `OncePerRequestFilter` (or `HandlerInterceptor`) extracts the JWT from the `Authorization` header, verifies via the `TokenVerifier` bean, and places the `Principal`/`AgentIdentity` in a request-scoped `ExecutionContext` copied at the HTTP filter boundary (one ThreadLocal read allowed at the boundary per AGENT_RULES §2.5). The controller then passes the identity into `PlanLoop.run(...)`.

### What happens when a caller supplies their own adapter?

Spring's conditional bean registration (`@ConditionalOnMissingBean`) lets a caller register their own `ChatModelPort`, `AuditPort`, etc., and the starter does not auto-configure a default for that port. This keeps the kernel provider-agnostic.

### How to avoid Spring types leaking into Tier 0?

The starter module depends on Tier 0 (`engine-core`, etc.) but Tier 0 never depends on the starter or on Spring. All Spring-specific annotations, `ApplicationContext`, `SmartLifecycle`, `Filter`, etc. live only in the Tier 2 starter module. Port interfaces reference only `java.base` + `jspecify`.

## Recommendation

**Promote A3a (Spring Boot `@Configuration` starter, Tier 2)** with A3c as the escape hatch for non-Spring embedders.

Rationale:
- Best developer experience for existing Spring applications (auto-configuration).
- Zero Spring in Tier 0 — purity preserved.
- `SmartLifecycle` + filter boundary give clean lifecycle and auth integration.
- `@ConditionalOnMissingBean` supports caller-supplied adapters.
- Rejected **A3c** as primary: poor DX, forces every Spring caller to hand-wire the kernel. Offer it as the documented programmatic alternative.

## Open questions

- Starter module name and coordinates (groupId/artifactId) — **UNSET — requires stakeholder input**.
- Whether Spring AI should be a transitive dependency of the starter or optional — defer to A4/A5 outcomes.

## Related

- ADR-003: Spring integration boundary (what Spring may and may not touch) (to be written, Phase B)
- `project/apex-platform/apex-auth/` — JWT filter pattern to reuse (Tier 2)
- AGENT_RULES §2.5: ThreadLocal at HTTP filter boundary only
