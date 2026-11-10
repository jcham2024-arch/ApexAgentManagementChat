# Capability Matrix (D5)

Matrix of enterprise capability × module showing tier, port attachment, module type, and status for each enterprise capability identified in NEXT-PHASE-INSTRUCTIONS §1.

Status values: `absent` (no work), `spike-only` (finding written, not specified), `specified` (ADR accepted, no spec yet), `implemented` (working code in the reactor).

| Capability | Tier | Port it attaches to | Module type | Status |
|------------|------|---------------------|-------------|--------|
| Deterministic A* planning | Tier 0 | `PlannerPort` | existing module (`engine-planner`) | implemented |
| Plan execution loop (OODA replan) | Tier 0 | (kernel; consumes `PlannerPort`, `RuntimePolicy`, `GatePlacementPolicy`, `ToolGatewayPort`) | existing module (`engine-core`) | implemented |
| Hash-chain audit (in-memory) | Tier 2 | `AuditPort` | existing module (`apex-audit`) | implemented |
| **Durable audit persistence** | Tier 1 adapter | `AuditPort` | new module (`apex-audit-persistence-sqlite`) | implemented |
| JWT auth (HS256) | Tier 2 | (apex-auth internal; `TokenVerifier`) | existing module (`apex-auth`) | implemented |
| **Asymmetric JWT (RS256 + JWKS)** | Tier 2 | `TokenVerifier` (apex-auth) | existing module (`apex-auth`, modified) | specified (ADR-002) |
| **Spring Boot starter** | Tier 2 | all ports as beans | new module (`starter-spring-boot` / TBD) | specified (ADR-003) |
| **Spring AI model adapter** | Tier 2 | `ChatModelPort`, `ToolExecutorPort` | new module (`adapter-spring-ai`) | specified (ADR-004) |
| **MCP tool-server adapter** | Tier 2 | `ToolExecutorPort` | new module (`adapter-mcp` / TBD) | specified (ADR-005) |
| **Tenancy isolation** | Tier 2 | `StoragePort`, `AuditPort`, `ApprovalPort` | existing module (`apex-tenant`, shell → impl) | specified (ADR-006) |
| **Tool sandboxing + v1 tools** | Tier 1 | `ToolExecutorPort`, `ToolGatewayPort`, `ToolPolicy` | existing module (`engine-tools-impl`, shell → impl) | specified (ADR-007) |
| Observability (metrics/telemetry) | Tier 1/2 | `MetricsPort`, `TelemetryPort` | existing module (`engine-metrics`) | implemented (no-op where unimplemented) |
| Checkpoint/resume | Tier 0/1 | `CheckpointPort` | existing (`engine-core` port; `engine-store-sqlite` impl planned) | implemented (port), planned (impl) |
| Vector store | Tier 1 | `VectorStorePort` | future (`engine-store-sqlite`, S4) | absent (future phase) |
| Secrets / key management | Tier 1/2 | `SecretsPort` | existing (`env vars` S3; `adapter-spring-vault` S3+) | implemented (env vars) |

## Notes

- Capabilities marked `specified` have an accepted ADR but no approved module build spec yet — they are ready to enter Phase D (spec readiness gate).
- `engine-tools` (Tier 0) itself contains no tool implementations; it hosts tool metadata/policy records only. Tool implementations are in `engine-tools-impl` (Tier 1).
- `apex-tenant` is a package-only shell awaiting the ADR-006 isolation model before implementation.
- No build spec exists yet for any of the `specified` capabilities — Phase C does not produce build specs; it produces the inputs (D4–D7) from which a build spec author works.
