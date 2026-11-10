# ADR-008: Versioning and compatibility policy for ports

**Status:** Accepted  
**Deciders:** Engineering team  
**Date:** 2026-11-10  
**Supersedes:** none  
**Depends on:** all spikes (A1–A7)

## Context

Ports are the Tier 0 / Tier 1 / Tier 2 seam. As new capabilities (durable audit, asymmetric JWT, Spring wiring, model providers, MCP tools, tenancy, tool sandboxing) are added, ports must evolve without breaking every adapter and caller. A compatibility policy is needed so port changes are deliberate, classified, and reviewed.

## Decision

**Adopt a port compatibility policy with three change classifications.**

1. **Unchanged** — port signature and semantics identical. Any adapter implements it without modification.
2. **Additive** — new method with a default implementation, or new record field with a default. Existing adapters compile unchanged; new behavior is opt-in. Non-breaking.
3. **Breaking** — removed/renamed method, changed signature, removed default, or changed semantics. Requires an ADR before proceeding and a coordinated adapter migration.

Rules:
- New capabilities arrive as a port interface plus a Tier 2 adapter; never as framework code in Tier 0 (AGENT_RULES §4).
- A port marked *breaking* requires an ADR (this ADR, ADR-008, governs the policy; the specific change gets its own ADR) before any module spec is approved.
- Do not open two module specs that modify the same port. Resolve the port change via ADR first, then spec the modules (NEXT-PHASE-INSTRUCTIONS §6 sequencing constraint).
- Tier 0 ports use only `java.base` + `jspecify`; no framework types (Spring, Jackson, etc.) may appear in port signatures.
- Determinism is preserved: no port change may introduce nondeterminism into plan generation or audit ordering (sequence tie-breakers and monotonic ordering are invariants).

## Consequences

### Positive
- Deliberate, reviewed port evolution.
- Breaking changes are visible and ADR-gated.
- Additive changes let adapters migrate incrementally.

### Negative
- Adds process overhead to port changes (classification + ADR for breaking).
- Every port modification must be checked for determinism impact.

## Related

- `docs/PORT-CATALOGUE.md` — the nine ports and their Tier 1/Tier 2 impls
- AGENT_RULES §2 / §4: Tier 0 purity; ports in Tier 0, adapters never leak into core
- NEXT-PHASE-INSTRUCTIONS §6: spec readiness gate and sequencing constraint
