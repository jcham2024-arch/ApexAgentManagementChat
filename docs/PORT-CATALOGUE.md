# PORT-CATALOGUE.md — The Nine Ports

The core owns these interfaces. Every adapter implements exactly one.
The core never imports an adapter. Tiers are defined in
`CONTEXT.md` §3.

| Port | Tier 1 impl | Tier 2 impl | First impl lands |
|---|---|---|---|
| `ChatModelPort` | `engine-llm-http` | `adapter-spring-ai` | S2.4 |
| `PlannerPort` | `engine-planner` | `adapter-spring-ai` | S2.2 |
| `ToolExecutorPort` | `engine-tools-impl` | (n/a — Tier 1 only) | S2.3 |
| `ApprovalPort` | `engine-cli` (terminal) | `adapter-spring-ai` (SSE) | S2.6 |
| `StoragePort` | `engine-store-sqlite` | `adapter-spring-jdbc` | S2.5 |
| `VectorStorePort` | `engine-store-sqlite` | `adapter-spring-ai` | S4 |
| `SecretsPort` | env vars | `adapter-spring-vault` | S3 |
| `AuditPort` | append-only file | `adapter-spring-audit` | S3 |
| `TelemetryPort` | no-op | `adapter-micrometer` | S2 (no-op) |

## Adding a port

1. Define the interface in `engine-core/.../port`.
2. Add supporting records to `engine-core/.../port/model`.
3. Add a row to this table with both Tier 1 and Tier 2 impls.
4. Add the port to `CONTEXT.md` §3.
5. Add a `@Test` to `PortTest` proving it is implementable.

## Adding an adapter

1. Create the module (Tier 1 or Tier 2 per `CONTEXT.md` §3).
2. Implement exactly one port.
3. Add the module to `CONTEXT.md` §4.
4. Add it to `PORT-CATALOGUE.md` under the port it implements.
