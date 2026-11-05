# ADR-007: Keep apex-auth and apex-audit as separate modules

**Status:** Decided  
**Deciders:** Engineering team  
**Date:** 2026-11-05

## Context

Phase 7 of the consolidation plan evaluates whether `apex-auth` (JWT/RBAC authentication filter) and `apex-audit` (hash-chained audit log with PII redaction) should be merged into a single module. Both are Tier 2 platform modules under `apex-platform/`.

## Context and evidence collected

### Criterion 1: Co-deployment — FAIL

| Module | Consumers | Dependency consumers |
|--------|-----------|---------------------|
| `apex-auth` | None | None |
| `apex-audit` | `engine-cli` (pom.xml only) | None |

No Java source file imports from either `com.apex.platform.auth` or `com.apex.platform.audit`. `engine-cli` declares `apex-audit` as a Maven dependency but does not import any class from it. The two modules are not co-deployed to any common consumer.

### Criterion 2: Shared dependency set — FAIL

| Module | Dependencies |
|----------|-------------|
| `apex-auth` | engine-core, apex-domain, jjwt-api, jjwt-impl(runtime), jjwt-gson(runtime), jspecify |
| `apex-audit` | engine-core, jspecify |

`apex-auth` depends on `jjwt-api`, `jjwt-impl`, and `jjwt-gson` (JWT library). `apex-audit` has no third-party runtime dependencies beyond `java.base` and `jspecify`. Merging would force `jjwt` on every consumer of `apex-audit` (currently `engine-cli`), forcing an unrelated JWT dependency on modules that only need audit logging.

### Criterion 3: Coupled release cadence — FAIL

| Module | Recent commits |
|----------|----------------|
| `apex-auth` | S3.0 (bootstrap), S3.1 (auth feature), S4.2 (ArchUnit test) |
| `apex-audit` | S3.0 (bootstrap), S3.3 (audit feature), S4.4 (tamper-evidence test) |

The two modules have independent commit histories. They were both created in S3.0 (commit `2222386`) but have evolved independently since then — `apex-auth` in S3.1 and S4.2, `apex-audit` in S3.3 and S4.4. They are not changed together in the same PRs.

### Criterion 4: Combined size — PASS

| Module | Main classes | Test classes | Total |
|----------|-------------|-------------|-------|
| `apex-auth` | 5 | 5 | 10 |
| `apex-audit` | 5 | 3 | 8 |
| **Combined** | **10** | **8** | **18** |

Combined production code is 10 classes — well under the ~25 class threshold for module overhead concern. However, this criterion alone is insufficient to justify a merge when the other three criteria fail.

## Decision

**KEEP SEPARATE** — `apex-auth` and `apex-audit` remain as separate Tier 2 modules.

### Rationale

Three of four merge criteria fail:

1. **Not co-deployed:** No consumer depends on both modules. `engine-cli` depends on `apex-audit` (but doesn't import its classes), and no module depends on `apex-auth`.

2. **Different dependency profiles:** `apex-auth` requires `jjwt` (JWT library) as a compile/runtime dependency. `apex-audit` has no third-party runtime dependencies beyond `jspecify`. Merging would force `jjwt` on `apex-audit` consumers (currently `engine-cli`), pulling an unrelated JWT library into modules that only need audit logging.

3. **Independent release cadence:** The modules have separate commit histories — `apex-auth` changes in S3.1 and S4.2, `apex-audit` changes in S3.3 and S4.4. They are not developed or released together.

The only criterion that passes is combined size (10 main classes combined), which is not sufficient to justify merging given the other three failures.

### Consequences

- **No code changes:** Both modules remain as-is with their current module boundaries and package structures (`com.apex.platform.auth` and `com.apex.platform.audit`).
- **No POM changes:** Both modules keep their separate POM files and dependency declarations.
- **Future re-evaluation:** Revisit this decision if:
  - A consumer emerges that depends on both `apex-auth` and `apex-audit` simultaneously, OR
  - Deployment artifacts are consolidated for other reasons, OR
  - `apex-auth` drops its `jjwt` dependency (making the dependency sets compatible).
- [ ] If `apex-auth` drops its `jjwt` dependency in the future, re-evaluate the merge decision as the dependency sets would then be compatible.

## Sources

- `apex-platform/apex-auth/pom.xml` — dependency declarations
- `apex-platform/apex-audit/pom.xml` — dependency declarations
- `apex-platform/apex-auth/src/main/java/` — 5 production classes
- `apex-platform/apex-audit/src/main/java/com/apex/platform/audit/` — 5 production classes
- `git log --all -- apex-platform/apex-auth/` — release cadence
- `git log --all -- apex-platform/apex-audit/` — release cadence
- `grep -RIn "apex-platform.apex-auth\|apex-platform.apex-audit" --include="*.java"` — consumer graph (no Java imports)
- `grep -RIn "apex-auth\|apex-audit" --include="pom.xml"` — Maven consumer graph
