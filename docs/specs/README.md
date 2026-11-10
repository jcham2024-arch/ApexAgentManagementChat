# Spec Readiness Checklist (D8)

Template and per-module application of the spec readiness gate from NEXT-PHASE-INSTRUCTIONS §6.

A module build spec may be authored **only** when all of the following are true:

| # | Readiness criterion |
|---|---------------------|
| D8.1 | A spike finding exists for the relevant capability (docs/spikes/) |
| D8.2 | The governing ADR(s) are accepted (docs/adr/) |
| D8.3 | The port contract change is classified (unchanged / additive / breaking) in docs/contracts/ports.md |
| D8.4 | The capability matrix row is filled in with tier and port target (docs/contracts/capability-matrix.md) |
| D8.5 | Blocking open questions are resolved (docs/contracts/open-questions.md) |
| D8.6 | The module's tier and dependency ceiling are stated explicitly |
| D8.7 | Test strategy is stated: unit, integration, and architectural enforcement |

## Per-module readiness assessment

| Module (capability) | D8.1 spike | D8.2 ADR | D8.3 port class. | D8.4 matrix row | D8.5 blocking Qs resolved | D8.6 tier/dep ceiling | D8.7 test strategy | Overall |
|---------------------|:----------:|:--------:|:----------------:|:---------------:|:-------------------------:|:-----------------------:|:------------------:|:-------:|
| Durable audit persistence (`AuditPort` durable impl) | ✓ A1 | ✓ ADR-001 | ✓ additive | ✓ | ✓ (Q1–Q3 closed: RPO=0/RTO=4h, p99≤10ms/≥100ops, key outside volume+key_id) | ✓ Tier 1 adapter; `java.base` + `jspecify` + SQLite | ✓ unit + integration + `CorePurityTest` for Tier 0 | **READY** |
| Asymmetric JWT (RS256 + JWKS) | ✓ A2 | ✓ ADR-002 | ✓ unchanged | ✓ | ✗ (Q4, Q5 blocking) | ✓ Tier 2; jjwt | ✓ unit + integration | blocked |
| Spring Boot starter | ✓ A3 | ✓ ADR-003 | ✓ unchanged | ✓ | ✗ (Q7 blocking) | ✓ Tier 2; Spring Boot | ✓ unit + integration | blocked |
| Spring AI model adapter | ✓ A4 | ✓ ADR-004 | ✓ unchanged | ✓ | ✗ (Q9 blocking) | ✓ Tier 2; Spring AI | ✓ unit + integration | blocked |
| MCP tool-server adapter | ✓ A5 | ✓ ADR-005 | ✓ unchanged | ✓ | ✗ (Q11 blocking) | ✓ Tier 2; MCP SDK | ✓ unit + integration | blocked |
| Tenancy isolation (`apex-tenant`) | ✓ A6 | ✓ ADR-006 | ✓ additive (`tenantId`) | ✓ | ✗ (Q13 blocking) | ✓ Tier 2; engine-core + jspecify | ✓ unit + integration | blocked |
| Tool sandboxing + v1 tools | ✓ A7 | ✓ ADR-007 | ✓ unchanged | ✓ | ✗ (Q15, Q16 blocking) | ✓ Tier 1 impl; `engine-tools-impl` | ✓ unit + integration | blocked |

## Result

**One module passes the spec readiness checklist: durable audit persistence (`AuditPort` durable implementation).**

All other specified capabilities are **blocked** on stakeholder input for the open questions listed in docs/contracts/open-questions.md (blocking: Q4, Q5, Q7, Q9, Q11, Q13, Q15, Q16). Q1–Q3 are now **closed** with decision notes recorded in `docs/contracts/decisions/`.

## Sequencing constraint (ADR-008 / NEXT-PHASE-INSTRUCTIONS §6)

Do not open two module specs that both modify the same port. Resolve the port change via ADR first, then spec the modules. Currently no two ready modules modify the same port, so durable audit persistence may proceed to spec authoring without conflict.

## Next step

A build spec for durable audit persistence may now be authored (it satisfies D8.1–D8.7). It must contain at minimum: module name/tier/location, purpose and enterprise gap, ports consumed/provided, public API surface and compatibility statement, dependency ceiling with justification, determinism impact statement, security impact statement, audit impact statement, failure/cancellation/timeout semantics, test plan with architectural enforcement tests, acceptance criteria in verifiable terms, and rollback/disable strategy.
