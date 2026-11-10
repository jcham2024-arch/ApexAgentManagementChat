# Non-Functional Requirements Capture (D6)

Enterprise-readiness NFRs per capability. Per NEXT-PHASE-INSTRUCTIONS §5.3: do not invent numbers. Where the business has not stated a requirement, record **"UNSET — requires stakeholder input"** and list it in the open-questions register (docs/contracts/open-questions.md).

## 1. Durable audit persistence

| NFR | Requirement | Status |
|-----|-------------|--------|
| Durability | Survive process restart; chain reconstructible from durable rows | Specified (ADR-001) |
| Recovery | RPO/RTO for audit store | **UNSET — requires stakeholder input** |
| Concurrency | Multi-writer safety for concurrent appenders | Specified (SQLite locking, ADR-001) |
| Latency | Per-append latency budget | **UNSET — requires stakeholder input** |
| Throughput | Appends per second | **UNSET — requires stakeholder input** |
| Key management | HMAC-SHA256 key storage/rotation | **UNSET — requires stakeholder input** |
| Observability | Audit emit latency, verification failures | **UNSET — requires stakeholder input** |
| Compatibility | Chain format stable across versions | Specified (canonical serialization) |

## 2. Asymmetric JWT (RS256 + JWKS)

| NFR | Requirement | Status |
|-----|-------------|--------|
| Durability | JWKS cache persistence across restarts | **UNSET — requires stakeholder input** |
| Concurrency | Concurrent verification requests | Specified (in-memory cache) |
| Latency | Per-token verification latency | **UNSET — requires stakeholder input** |
| Key management | JWKS refresh interval, key rotation, `kid` handling | **UNSET — requires stakeholder input** |
| Clock skew | `exp`/`nbf` tolerance window | **UNSET — requires stakeholder input** |
| Observability | Verification success/failure rate, key cache hit rate | **UNSET — requires stakeholder input** |
| Compatibility | HS256 backward compatibility | Specified (ADR-002) |

## 3. Spring Boot starter

| NFR | Requirement | Status |
|-----|-------------|--------|
| Concurrency | Concurrent kernel embeddings in one context | **UNSET — requires stakeholder input** |
| Latency | Starter auto-configuration startup time | **UNSET — requires stakeholder input** |
| Key management | Secrets injected via `SecretsPort` (env vars / Vault) | Specified (ADR-003) |
| Observability | Bean registration, lifecycle start/stop events | **UNSET — requires stakeholder input** |
| Compatibility | Zero Spring in Tier 0 | Specified (ADR-003) |

## 4. Spring AI model adapter

| NFR | Requirement | Status |
|-----|-------------|--------|
| Latency | Model inference latency (provider-dependent) | **UNSET — requires stakeholder input** |
| Concurrency | Concurrent chat requests | **UNSET — requires stakeholder input** |
| Failure semantics | Provider error → `ChatResponse` error, never thrown across port | Specified (ADR-004) |
| Cancellation | `CancellationToken` checked between stream chunks | Specified (ADR-004) |
| Timeout | Provider request timeout | **UNSET — requires stakeholder input** |
| Observability | Token usage, stream chunk count | **UNSET — requires stakeholder input** |
| Compatibility | Determinism confined to adapter boundary | Specified (ADR-004) |

## 5. MCP tool-server adapter

| NFR | Requirement | Status |
|-----|-------------|--------|
| Transport | HTTP + Streamable JSON-RPC (primary), stdio (local) | Specified (ADR-005) |
| Concurrency | Concurrent tool calls to same server | **UNSET — requires stakeholder input** |
| Latency | Per-call latency | **UNSET — requires stakeholder input** |
| Timeout | Default 30s per call | Specified (ADR-005) |
| Cancellation | Client-initiated JSON-RPC cancel | Specified (ADR-005) |
| Key management | MCP SDK license compatibility (Apache/MIT/BSD/ISC) | **UNSET — requires stakeholder input** |
| Observability | Tool call success/failure, duration | **UNSET — requires stakeholder input** |
| Compatibility | No MCP types in Tier 0 | Specified (ADR-005) |

## 6. Tenancy isolation

| NFR | Requirement | Status |
|-----|-------------|--------|
| Concurrency | Per-tenant isolation under concurrent access | Specified (fail-closed, ADR-006) |
| Failure mode | Absent tenant context → fail closed, no write | Specified (ADR-006) |
| Observability | Per-tenant audit/query metrics | **UNSET — requires stakeholder input** |
| Compatibility | `TenantId` carried explicitly (no ambient ThreadLocal) | Specified (ADR-006) |

## 7. Tool sandboxing + v1 tools

| NFR | Requirement | Status |
|-----|-------------|--------|
| Concurrency | Concurrent tool executions | **UNSET — requires stakeholder input** |
| Latency | Per-tool execution latency | **UNSET — requires stakeholder input** |
| Timeout | Per-tool wall-clock timeout | Specified (ADR-007) |
| Cancellation | `CancellationToken` checked before start and at checkpoints | Specified (ADR-007) |
| Key management | None (no credentials in v1 tools) | N/A |
| Observability | Per-tool call audit (name, args redacted, outcome, duration) | Specified (ADR-007) |
| Compatibility | Path confinement, network allow-list, command allow-list | Specified (ADR-007) |

## 8. Determinism (cross-cutting)

| NFR | Requirement | Status |
|-----|-------------|--------|
| Plan determinism | Same inputs → same plan, every run | Specified (Tier 0 invariant) |
| Audit ordering | Monotonic sequence, hash chain across restarts | Specified (ADR-001) |
| Compatibility | No nondeterminism introduced in Tier 0 | Specified (ADR-008) |

## Open items carried to register

The following NFRs are unset and are recorded in docs/contracts/open-questions.md as blocking or non-blocking:
- Audit RPO/RTO
- Audit per-append latency & throughput
- HMAC key storage/rotation
- JWKS refresh interval, clock-skew tolerance
- HS256 deprecation sunset date
- Starter auto-config startup time
- Model inference latency & throughput
- Provider request timeout
- Concurrent tool calls & per-tool latency
- MCP SDK license compatibility
- Per-tenant observability metrics
