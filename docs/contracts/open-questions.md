# Open Questions Register (D7)

Single register of every unresolved question surfaced by Phases A–C. Each question has an owner and a blocking status. Per NEXT-PHASE-INSTRUCTIONS §6, a spec cannot be approved while a blocking question touching it remains open.

| # | Question | Surfaced in | Owner | Blocking status | Blocks |
|---|----------|-------------|-------|-----------------|--------|
| Q1 | Audit RPO/RTO for the durable audit store | A1 spike / NFR §1 | Engineering team / stakeholder | Non-blocking (durable impl proceeds with SQLite defaults) | Audit persistence spec |
| Q2 | Audit per-append latency and throughput budget | A1 spike / NFR §1 | Stakeholder | Non-blocking | Audit persistence spec |
| Q3 | HMAC-SHA256 key storage and rotation for audit chain | A1 spike / NFR §1 | Security / stakeholder | Non-blocking (key passed in; rotation future) | Audit persistence spec |
| Q4 | JWKS endpoint URL and refresh interval | A2 spike / ADR-002 / NFR §2 | Stakeholder | Blocking | Asymmetric JWT spec |
| Q5 | Clock-skew tolerance window (`exp`/`nbf`) | A2 spike / NFR §2 | Stakeholder | Blocking | Asymmetric JWT spec |
| Q6 | HS256 deprecation sunset date | A2 spike | Stakeholder | Non-blocking | Asymmetric JWT spec (future) |
| Q7 | Spring Boot starter module name/coordinates (groupId/artifactId) | A3 spike / ADR-003 / NFR §3 | Engineering team | Blocking | Spring starter spec |
| Q8 | Whether Spring AI is a transitive or optional starter dependency | A3/A4 spike | Engineering team | Non-blocking | Spring starter spec |
| Q9 | Spring AI version and provider (OpenAI-compatible vs Azure vs Ollama) | A4 spike / NFR §4 | Stakeholder | Blocking | Spring AI adapter spec |
| Q10 | Whether streaming is required for v1 or can be deferred | A4 spike / ADR-004 | Stakeholder | Non-blocking | Spring AI adapter spec |
| Q11 | MCP SDK/library choice and license compatibility (Apache/MIT/BSD/ISC) | A5 spike / NFR §5 | Engineering team / legal | Blocking | MCP adapter spec |
| Q12 | MCP stdio vs HTTP transport default | A5 spike / ADR-005 | Stakeholder | Non-blocking | MCP adapter spec |
| Q13 | Whether `tenantId` should be added to `AuditEntry` model | A6 spike / ADR-006 | Engineering team | Blocking | Tenancy spec (Tier 0 record change) |
| Q14 | Multi-tenant query performance and index strategy (SQLite) | A6 spike / NFR §6 | Stakeholder | Non-blocking | Tenancy storage spec |
| Q15 | Exact host allow-list for `http` tool (v1) | A7 spike / NFR §7 | Security / stakeholder | Blocking | Tool sandboxing spec |
| Q16 | Exact command allow-list for `run` tool (v1) | A7 spike / NFR §7 | Security / stakeholder | Blocking | Tool sandboxing spec |
| Q17 | Tool result size caps and duration caps | A7 spike / NFR §7 | Stakeholder | Non-blocking | Tool sandboxing spec |

## Blocking questions (must be resolved before spec approval)

Q4, Q5, Q7, Q9, Q11, Q13, Q15, Q16.

## Non-blocking questions (spec may proceed with documented assumptions)

Q1, Q2, Q3, Q6, Q8, Q10, Q12, Q14, Q17.

## Process

- The owner of each blocking question must provide a resolution before the corresponding module build spec is approved.
- Resolutions are recorded here (status updated, answer noted) — do not close a question without recording the answer.
- When a blocking question is resolved, update the corresponding ADR/finding and proceed to Phase D (spec readiness gate) for the affected module.
