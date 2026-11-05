# docs/PORT-RECONCILIATION.md — Side-by-Side Port Table

**Version:** 1.0 · **Status:** Draft — Harness (`Google-Ai-repo@Harness`) not present in this workspace; verified against `ApexAgentManagementChat` `main` branch only

This file reconciles port definitions across the two repositories being merged:
- **Harness** (`Google-Ai-repo@Harness`, branch: `Harness`) — NOT PRESENT in this workspace; paths from NEXT-STEPS.md §2
- **Apex** (`ApexAgentManagementChat`, branch: `main`) — verified against live branches

For each port, the table shows: port name, where it lives in each repo, and the consolidation decision (keep / merge / add).

## Port reconciliation table

| Port | Harness location | Apex location | Tier | Decision | Notes |
|---|---|---|---|---|---|
| `ChatModelPort` | `core-engine/` | `engine-core/.../port/` → `engine-llm-http` (impl) | 0 | Merge | Apex has richer record model; Harness impl is `HttpURLConnection`-based |
| `PlannerPort` | Not in Harness | `engine-core/.../port/` → `engine-planner` (impl) | 0 | Keep | Harness has no planner port; Apex A* is deterministic fallback |
| `ToolExecutorPort` | `core-engine/` | `engine-core/.../port/` → `engine-tools-impl` (impl) | 0 | Merge | Harness has tool execution; Apex has file + HTTP tools |
| `ApprovalPort` | Not in Harness | `engine-core/.../port/` → `engine-cli` terminal (impl) | 0 | Keep | Harness has no approval port; Apex S2.6 HITL gate |
| `StoragePort` | `core-engine/` | `engine-core/.../port/` → `engine-store-sqlite` (impl) | 0 | Merge | Apex has tenant-scoped SQLite; Harness has in-memory only |
| `VectorStorePort` | Not in Harness | `engine-core/.../port/` → `engine-store-sqlite` (impl) | 0 | Keep | Harness has no vector store port |
| `SecretsPort` | Not in Harness | `engine-core/.../port/` → env vars (impl) | 0 | Keep | Harness has no secrets port; Apex S3 |
| `AuditPort` | `security-interface/` (hash chain) | `engine-core/.../port/` → `InMemoryAuditLog` (impl) | 0 | Merge | Harness has SecurityInterlock + hash chain; Apex has InMemoryAuditLog; keep both, unify interface |
| `TelemetryPort` | Not in Harness | `engine-core/.../port/` → no-op (impl) | 0 | Keep | Harness has no telemetry port; Apex S2 no-op |

## New ports to add (not in either repo)

| Port | Reason | Phase |
|---|---|---|
| `JsonCodecPort` | Single JSON codec abstraction; replaces `MiniJson` (Harness) and hand-rolled parser (Apex) | S2 |
| `HttpTransportPort` | Mandatory constructor arg in providers; fixes Apex's nullable-transport smell | S2 |
| `HttpTransportPort` (Android) | OkHttp-based provider for Android | S3 |

## Ports unique to Harness (not in Apex)

| Port/Type | Harness location | Notes |
|---|---|---|
| `SecurityInterlock` | `security-interface/` | Security interlock; freeze during merge (ADR-0007) |
| `RiskLevel` (L0–L4) | `security-interface/` | Tool risk classification; Apex hardcodes L0–L4 per tool |
| Audit chain | `security-interface/` | Hash-chained audit; preserve verbatim, do not refactor |

## Ports unique to Apex (not in Harness)

| Port | Apex location | Notes |
|---|---|---|
| `PlannerPort` | `engine-core/.../port/` → `engine-planner` | Deterministic A* planner; becomes fallback/test double for LLM planner |
| `ApprovalPort` | `engine-core/.../port/` → `engine-cli` terminal | Human-in-the-loop gate for L3+ tools |
| `VectorStorePort` | `engine-core/.../port/` → `engine-store-sqlite` | Vector storage; S4 |
| `SecretsPort` | `engine-core/.../port/` → env vars | Secret retrieval; S3 |
| `TelemetryPort` | `engine-core/.../port/` → no-op | Telemetry; S2 no-op |

## Tier assignments (CONTEXT.md §4)

| Tier | Modules | Allowed dependencies |
|---|---|---|
| **0 — Pure core** | Port definitions + pure logic | `java.base`, `org.jspecify` only |
| **1 — Local adapters** | Port implementations | Tier 0 + JDK IO + HTTP |
| **2 — Enterprise adapters** | Spring, Google, Jackson, Micrometer integrations | Tier 0 + Spring + Google + Jackson + Micrometer |

## Enforcement gates

| Gate | Mechanism | Scope |
|---|---|---|
| Gate 1 | Maven Enforcer (banned deps) | Tier 0 modules |
| Gate 2 | jdeps bytecode scan | Tier 0 jars |
| Gate 3 | ArchUnit `CorePurityTest` | Each Tier 0 module |

## Decisions pending

| Decision | ADR | Status |
|---|---|---|
| JSON codec | ADR-0003 | Not written |
| HTTP client | ADR-0004 | Not written |
| Async model | ADR-0005 | Not written |
| DI approach | ADR-0006 | Not written |
| Namespace/package scheme | ADR-0008 | Not written |
