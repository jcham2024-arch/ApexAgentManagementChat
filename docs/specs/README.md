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
| Durable audit persistence (`AuditPort` durable impl) | ✓ A1 | ✓ ADR-001 | ✓ additive | ✓ | ✓ (Q1–Q3 closed) | ✓ Tier 1 adapter; `java.base` + `jspecify` + SQLite | ✓ unit + integration + `CorePurityTest` for Tier 0 | **READY** |
| Asymmetric JWT (RS256 + JWKS) | ✓ A2 | ✓ ADR-002 | ✓ unchanged | ✓ | ✓ (Q4, Q5 closed) | ✓ Tier 2; jjwt | ✓ unit + integration | **READY** |
| Spring Boot starter | ✓ A3 | ✓ ADR-003 | ✓ unchanged | ✓ | ✓ (Q7, Q9 closed) | ✓ Tier 2; Spring Boot | ✓ unit + integration | **READY** |
| Spring AI model adapter | ✓ A4 | ✓ ADR-004 | ✓ unchanged | ✓ | ✓ (Q11 closed) | ✓ Tier 2; Spring AI | ✓ unit + integration | **READY** |
| MCP tool-server adapter | ✓ A5 | ✓ ADR-005 | ✓ unchanged | ✓ | ✗ (Q12 non-blocking; Q11 closed) | ✓ Tier 2; MCP SDK | ✓ unit + integration | **READY** (Q12 non-blocking) |
| Tenancy isolation (`apex-tenant`) | ✓ A6 | ✓ ADR-006 | ✓ additive (`tenantId`) | ✓ | ✓ (Q13 closed) | ✓ Tier 2; engine-core + jspecify | ✓ unit + integration | **READY** |
| Tool sandboxing + v1 tools | ✓ A7 | ✓ ADR-007 | ✓ unchanged | ✓ | ✓ (Q15, Q16 closed) | ✓ Tier 1 impl; `engine-tools-impl` | ✓ unit + integration | **READY** |

## Result

**All seven specified capabilities pass the spec readiness checklist.**

Blocking questions Q4–Q16 are now **closed** with decision notes recorded in `docs/contracts/decisions/`. Remaining non-blocking questions (Q6, Q8, Q10, Q12, Q14, Q17) do not block spec authoring.

## Authored specs

| Spec | Path | Status |
|------|------|--------|
| Durable audit persistence | `docs/specs/audit-persistence-spec.md` | Authored (implementation merged) |
| Asymmetric JWT / RS256 + JWKS | `docs/specs/asymmetric-jwt-spec.md` | Authored (ready for approval) |
| Spring Boot starter | `docs/specs/spring-boot-starter-spec.md` | Authored (ready for approval) |
| Spring AI model adapter | `docs/specs/spring-ai-adapter-spec.md` | Authored (ready for approval) |
| Tenancy isolation | `docs/specs/tenancy-spec.md` | Authored (ready for approval) |
| Tool sandboxing + v1 tools | `docs/specs/tool-sandboxing-spec.md` | Authored (ready for approval) |

MCP adapter spec is deferred until sandboxing spec is approved (dependency per closure sequence).

## Sequencing constraint (ADR-008 / NEXT-PHASE-INSTRUCTIONS §6)

Do not open two module specs that both modify the same port. Resolve the port change via ADR first, then spec the modules. All seven specs above modify distinct ports or consume existing ports additively, so no conflicts exist.

## Next step

Approve specs in dependency order:
1. Asymmetric JWT (Cluster 1 — smallest, unblocks Spring starter)
2. Spring Boot starter (Cluster 2 — enterprise adoption blocker)
3. Spring AI model adapter (Cluster 3 — depends on starter bean surface)
4. Tenancy isolation (Cluster 4 — largest design problem)
5. Tool sandboxing (Cluster 4 — largest design problem, parallel with tenancy)
6. MCP adapter (after sandboxing approved)
