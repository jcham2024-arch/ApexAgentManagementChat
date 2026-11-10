# ADR-003: Spring integration boundary (what Spring may and may not touch)

**Status:** Accepted  
**Deciders:** Engineering team  
**Date:** 2026-11-10  
**Supersedes:** none  
**Depends on:** Spike A3 (docs/spikes/A3-spring-boot-starter.md)

## Context

Existing Spring applications need to embed the Apex kernel. Tier 0 must remain free of Spring and all enterprise dependencies (AGENT_RULES §2). A Spring Boot starter must therefore live in Tier 2 and depend on Tier 0 only through ports.

## Decision

**Adopt a Tier 2 Spring Boot `@Configuration` starter (A3a) with a programmatic escape hatch (A3c).**

- The starter exposes `PlanLoop`, `AuditPort`, `ChatModelPort`, `ToolExecutorPort`, `StoragePort`, `CheckpointPort`, `RuntimePolicy`, `GatePlacementPolicy`, `SecretsPort`, `MetricsPort`, `TelemetryPort` as Spring beans, each with a default Tier 1 implementation overridable via `@ConditionalOnMissingBean`.
- `PlanLoop` lifecycle is managed via Spring `SmartLifecycle`: `start()` begins the loop with a `CancellationToken`; `stop()` cancels the token and drains in-flight steps; the context shutdown hook triggers `stop()`.
- Auth integrates as a Spring `OncePerRequestFilter`: extracts the JWT, verifies via the `TokenVerifier` bean, and places the `Principal`/`AgentIdentity` in a request-scoped `ExecutionContext` copied at the HTTP filter boundary (one ThreadLocal read allowed, per AGENT_RULES §2.5).
- Callers may supply their own adapter beans (`@ConditionalOnMissingBean` skips auto-configuration for that port).
- Tier 0 never imports Spring or any starter class. All Spring annotations, `ApplicationContext`, `SmartLifecycle`, `Filter`, etc. live only in the Tier 2 starter module.

## Alternatives considered

| Option | Verdict | Reason |
|--------|---------|--------|
| Tier 2 `@Configuration` starter (A3a) | **Accepted** | Best DX for Spring apps; zero Spring in Tier 0; clean lifecycle/auth integration. |
| Programmatic kernel + Spring adapter only (A3c) | Supported as escape hatch | Documented for non-Spring embedders; poor DX as the primary path. |

## Consequences

### Positive
- Existing Spring applications embed the kernel with auto-configuration.
- Tier 0 purity preserved.
- Caller-supplied adapters supported without modifying the starter.

### Negative
- Starter module name/coordinates and Spring AI optional-dependency decision are deployment-specific (UNSET — requires stakeholder input).

## Related

- docs/spikes/A3-spring-boot-starter.md — the spike finding behind this decision
- AGENT_RULES §2.5: ThreadLocal at HTTP filter boundary only
- `project/apex-platform/apex-auth/` — JWT filter pattern to reuse (Tier 2)
- docs/contracts/decisions/Q7-Q9-spring-starter.md — Q7/Q9 decision note (apex-spring-boot-starter, @ConditionalOnMissingBean)
