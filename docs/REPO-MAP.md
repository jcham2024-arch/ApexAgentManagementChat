# docs/REPO-MAP.md — Repository Path Map (verified)

**Version:** 1.0 · **Status:** Verified against live branches · **Source:** NEXT-STEPS.md §2

This file maps the path references in NEXT-STEPS.md §2 to the actual paths in this workspace
(`ApexAgentManagementChat`). The `Google-Ai-repo@Harness` repository referenced in NEXT-STEPS.md
is **not present** in this workspace; only `ApexAgentManagementChat` is available for verification.

## 1. Repository layout (ApexAgentManagementChat, `main` branch)

```
ApexAgentManagementChat/
├── pom.xml                          # Layer 1 Maven aggregator (apex-chat parent)
├── apex/pom.xml                     # Layer 2 Maven aggregator (apex engine)
├── AGENT_RULES.md                   # Build behavior contract (always loaded)
├── CONTEXT.md                       # Project map & file index (always loaded)
├── ROADMAP.md                       # Delivery phases and versions
├── STANDARDS.md                     # Language coding standards
├── SALVAGE.md                       # Prior-repo patterns
├── LOCAL-BUILD.md                   # Local build recipe (moved from root per NEXT-STEPS.md §2.3)
├── .github/workflows/ci.yml         # CI: Maven build + Gate G2 (jdeps)
├── docs/
│   ├── PORT-CATALOGUE.md            # Nine ports + tier assignments
│   ├── INVARIANTS.md                # Testable claims (K/E/C)
│   ├── LOCAL-BUILD.md               # Local Maven build recipe
│   ├── apex-chat-spec/              # Layer 1 POJO spec (11 parts, only 01 present)
│   │   ├── 01-charter-and-principles.md
│   │   └── (02-11 not present)
│   └── (S1-GATE-PROOFS.md)
├── apex-chat/                       # Layer 1 pure POJOs
│   └── apex-chat-common/            # TenantId, TypedId (+ CorePurityTest)
├── apex-core/                       # Layer 2 engine (Maven aggregator)
│   ├── engine-core/                 # Tier 0: 9 ports + PlanLoop + CancellationToken
│   ├── engine-planner/              # Tier 0: A* planner
│   ├── engine-store/                # Tier 0: storage port skeleton (package-info only)
│   ├── engine-tools/                # Tier 0: tools port skeleton (package-info only)
│   └── (source at apex-core/*, NOT engine-core/*)
├── apex-platform/                   # Tier 2 (Maven aggregator)
│   ├── apex-auth/                   # JWT auth filter + RBAC
│   ├── apex-audit/                  # Hash-chained audit log + PII redaction
│   └── apex-tenant/                 # Tenant isolation skeleton
├── engine-cli/                      # Tier 1: ReAct runner + CLI entrypoint
├── engine-llm-http/                 # Tier 1: OpenAI-compatible ChatModelPort impl
├── engine-json/                     # Tier 0: shared JSON parser (S5)
├── engine-llm-http/                 # Tier 1: OpenAI-compatible ChatModelPort impl
├── engine-memory/                   # Tier 0: memory types + router + compactor (S4)
├── engine-sandbox/                  # Tier 1: SandboxPort stub (S4)
├── engine-store-sqlite/             # Tier 1: SQLite StoragePort impl
└── engine-tools-impl/               # Tier 1: read/write/list/run_command/http_get tools
```

## 2. Path corrections vs NEXT-STEPS.md §2

| NEXT-STEPS.md §2 reference | Actual path in this workspace | Status |
|---|---|---|
| `engine-core/` | `apex-core/engine-core/` | Corrected |
| `engine-planner/` | `apex-core/engine-planner/` | Corrected |
| `engine-store-sqlite/` | `engine-store-sqlite/` | Matches |
| `engine-tools-impl/` | `engine-tools-impl/` | Matches |
| `engine-llm-http/` | `engine-llm-http/` | Matches |
| `engine-cli/` | `engine-cli/` | Matches |
| `apex-chat-common/` | `apex-chat/apex-chat-core/` | Corrected |
| `apex-auth/` | `apex-platform/apex-auth/` | Corrected |
| `apex-tenant/` | `apex-platform/apex-tenant/` | Corrected |
| `PORT-CATALOGUE.md` | `docs/PORT-CATALOGUE.md` | Corrected |
| `AGENT_RULES.md` | `AGENT_RULES.md` (root) | Matches |
| `LOCAL-BUILD.md` | `docs/LOCAL-BUILD.md` | Corrected |
| `Google-Ai-repo@Harness/` | **Not present** | Cannot verify |
| `core-engine/` | **Not present** | Cannot verify |
| `security-interface/` | **Not present** | Cannot verify |

## 3. Module inventory

### Tier 0 — Pure core (`java.base` + `org.jspecify` only)

| Module | Path | Role |
|---|---|---|
| `apex-chat-common` | `apex-chat/apex-chat-core/` | `TenantId`, `TypedId`, identity types |
| `engine-core` | `apex-core/engine-core/` | 9 ports, `PlanLoop`, `CancellationToken` |
| `engine-planner` | `apex-core/engine-planner/` | `AStarPlanner`, GOAP actions/goals |
| `engine-memory` | `engine-memory/` | `MemoryKind`, `MemoryQuery`, `MemoryRouter`, `ContextCompactor`, `BoundedWorkingMemory` |

### Tier 1 — Local adapters (`Tier 0 + JDK IO + HTTP`)

| Module | Path | Role |
|---|---|---|
| `engine-tools-impl` | `engine-tools-impl/` | `read_file`, `write_file`, `list_files`, `run_command`, `http_get` |
| `engine-store-sqlite` | `engine-store-sqlite/` | `StoragePort` (SQLite, WAL, tenant isolation) |
| `engine-llm-http` | `engine-llm-http/` | `ChatModelPort` (OpenAI-compatible, SSE) |
| `engine-sandbox` | `engine-sandbox/` | `SandboxPort` stub |
| `engine-cli` | `engine-cli/` | ReAct runner, task YAML loader, CLI entrypoint |
| `engine-json` | `engine-json/` | Shared JSON parser (S5) |

### Tier 2 — Enterprise adapters (`Tier 0 + Spring + Google + Jackson + Micrometer`)

| Module | Path | Role |
|---|---|---|
| `apex-auth` | `apex-platform/apex-auth/` | JWT auth filter + RBAC |
| `apex-audit` | `apex-platform/apex-audit/` | Hash-chained audit log + PII redaction |
| `apex-tenant` | `apex-platform/apex-tenant/` | Tenant isolation skeleton |

## 4. Ports (from docs/PORT-CATALOGUE.md)

| Port | Tier 1 impl | Tier 2 impl | First impl |
|---|---|---|---|
| `ChatModelPort` | `engine-llm-http` | `adapter-spring-ai` | S2.4 |
| `PlannerPort` | `engine-planner` | `adapter-spring-ai` | S2.2 |
| `ToolExecutorPort` | `engine-tools-impl` | (Tier 1 only) | S2.3 |
| `ApprovalPort` | `engine-cli` (terminal) | `adapter-spring-ai` (SSE) | S2.6 |
| `StoragePort` | `engine-store-sqlite` | `adapter-spring-jdbc` | S2.5 |
| `VectorStorePort` | `engine-store-sqlite` | `adapter-spring-ai` | S4 |
| `SecretsPort` | env vars | `adapter-spring-vault` | S3 |
| `AuditPort` | append-only file | `adapter-spring-audit` | S3 |
| `TelemetryPort` | no-op | `adapter-micrometer` | S2 (no-op) |

## 5. Build system

- **Build tool:** Maven (two aggregators: `pom.xml` for Layer 1, `apex/pom.xml` for Layer 2)
- **Java level:** Release 21
- **Test framework:** JUnit 5 + AssertJ + ArchUnit (Gate 3)
- **Enforcement gates:** Maven Enforcer (Gate 1), jdeps bytecode scan (Gate 2), ArchUnit CorePurityTest (Gate 3)
- **Local build requires:** `-Dmaven.repo.local=$PWD/.m2` and `-DargLine="-Djava.io.tmpdir=..."`

## 6. Not present in this workspace

The following items from NEXT-STEPS.md §2 reference the `Google-Ai-repo@Harness` repository, which is **not available** here:

- `Google-Ai-repo/app/` — Android app consumer
- `Google-Ai-repo/core-engine/` — candidate shared core (pure Java 11)
- `Google-Ai-repo/security-interface/` — SecurityInterlock, RiskLevel, audit chain
- `Google-Ai-repo/build.gradle` / `settings.gradle`

These paths cannot be verified until the Harness repository is available in this workspace.
