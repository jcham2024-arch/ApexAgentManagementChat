# SUPPLEMENTARY INSTRUCTIONS: Harvesting from ApexAgentManagementChat

## Relationship to the Main Instructions

This document **amends and supplements** `PROJECT_AGENT_INSTRUCTIONS.md`. It does **not** replace it. Read the main instructions first; then apply this document when executing **Phase A** (provider config), **Phase B** (core engine), and **Phase D** (security interface).

**Scope of this document:** what to harvest from `https://github.com/jcham2024-arch/ApexAgentManagementChat`, what to adapt, what to read-only reference, and what to discard. Nothing here overrides the non-negotiable constraints in the main instructions (Java 11 core, zero third-party runtime deps, Keystore-only secrets, one OpenAI-compatible client, strict dependency chain).

---

## Repository Overview and Warning

`ApexAgentManagementChat` is a **multi-module Maven project on Java 21**, built for desktop/enterprise environments. It is **not** Android-compatible as-is.

- Its build system is **Maven**, not Gradle. Do not try to import the POMs.
- Its runtime APIs include `java.net.http`, SnakeYAML, and Spring-oriented enterprise modules.
- Its value to this project is **architectural and interface-level**, not as drop-in code.

> ⚠️ **Before copying anything:** read the repository's `LICENSE` file. If the license does not permit redistribution in a closed or mixed-license Android APK, **stop and report**. Do not copy code whose license is unclear. When in doubt, copy the *concept* and rewrite the code.

The module names referenced below (`engine-core`, `engine-cli`, `engine-llm-http`, `engine-planner`, `apex-platform`, `apex`) are the module directories at the repository root. **Verify exact paths during clone** using:

```bash
git clone https://github.com/jcham2024-arch/ApexAgentManagementChat.git /tmp/apex-ref
cd /tmp/apex-ref
ls -1                          # confirm module names
find . -name "pom.xml"         # confirm module boundaries
grep -rn "interface .*Port" --include=*.java   # locate the port catalogue
grep -rn "enum RiskLevel" --include=*.java     # locate risk model
grep -rn "class ProviderConfigLoader" --include=*.java  # locate provider config
```

If any of these commands return nothing, the module layout differs from this document — **stop and report** with the actual layout before proceeding.

---

## Harvest Decision Table

| Component | Location (verify on clone) | Action | Destination |
| :--- | :--- | :--- | :--- |
| Nine port interfaces | `engine-core/**/port/*.java` | **COPY** (with edits) | `core-engine/src/main/java/.../port/` |
| Port supporting model types | `engine-core/**/port/model/*.java` | **ADAPT** (records → final classes) | `core-engine/.../port/model/` |
| `RiskLevel` enum + default mapping | `engine-cli/**/RiskLevel.java` (or similar) | **COPY** | `security-interface/.../policy/` |
| `ProviderConfigLoader` logic | `engine-llm-http/**/ProviderConfigLoader.java` | **ADAPT as specification** | `app/data/provider/ProviderPresets.kt` |
| `AuditPort` hash-chain design | `engine-core/**/port/AuditPort.java` + `AuditEntry` | **REFERENCE** | Audit design in `:security-interface` |
| ReAct runner (stub + real) | `engine-cli/**/ReAct*.java` | **REFERENCE ONLY** | Guide for porting Leanarch2 loop |
| Task-file loader (SnakeYAML) | `engine-cli/**/*TaskLoader*.java` | **REFERENCE ONLY** | Do not copy; schema only |
| A* / GOAP planner | `engine-planner/**` | **REFERENCE ONLY** | Out of scope for lean Android |
| `engine-llm-http` implementation | `engine-llm-http/**` | **DISCARD** | Replace with `OpenAiCompatibleClient` |
| `apex-platform` (audit/auth/tenant) | `apex-platform/**` | **DISCARD** | Enterprise-only; irrelevant |
| `apex` aggregator module | `apex/**` | **DISCARD** | Maven aggregator, no app code |

---

## 1. COPY: The Nine Port Interfaces (`engine-core`)

These are the **highest-value asset** in the repository. They define the core abstraction surface and align directly with the three-layer architecture in the main instructions.

### The catalogue

| Port | Responsibility | Android implementation lives in |
| :--- | :--- | :--- |
| `ChatModelPort` | LLM chat / streaming | `:core-engine` (`OpenAiCompatibleClient`) |
| `PlannerPort` | Planning / replanning | `:core-engine` (ported Leanarch2 loop) |
| `ToolExecutorPort` | Tool invocation | `:security-interface` (post-interlock dispatch) |
| `ApprovalPort` | Human-in-the-loop gating | `:security-interface` (Compose dialog) |
| `StoragePort` | Session/step persistence | `:app` or `:security-interface` (Room/SQLite) |
| `VectorStorePort` | Embedding storage | **Defer** — not required for MVP |
| `SecretsPort` | API key retrieval | `:app` (`ProviderRepository` + Keystore) |
| `AuditPort` | Append-only audit trail | `:security-interface` |
| `TelemetryPort` | Metrics / tracing | Optional; logcat-backed stub |

### Copy procedure

1. Create `core-engine/src/main/java/<your.package>/port/` (choose a package; do **not** keep `jspecify`-based package names if they leak into the API).
2. Copy the **interface files only** into that directory.
3. Copy `port/model/*` supporting types into `core-engine/.../port/model/`.
4. Apply the edits in the next subsection before compiling.

### Mandatory edits after copying

| Issue | Why | Fix |
| :--- | :--- | :--- |
| **`jspecify` annotations** | Compile-time only, but adds a dependency declaration | **Remove every `jspecify` import and annotation.** Either leave unannotated or replace with `androidx.annotation.Nullable` / `NonNull` (compile-only). Do **not** add a runtime dependency. |
| **`record` types in `port/model`** | Records require Java 16+; `:core-engine` is **Java 11** | Convert every `record` to a `final class` with a constructor, accessors, `equals`, `hashCode`, and `toString`. This is mechanical but must be done — the core will not compile otherwise. |
| **Desktop-oriented types** | e.g., `java.nio.file.Path` in method signatures | Replace with `java.io.File` or `String` (path string). `Path` is absent from Android API 26. |
| **Package name collision** | Apex packages will not match your app's | Rewrite `package` declarations to your chosen namespace. |
| **`Optional<T>` in signatures** | Allowed on Android (desugared), but inconsistent | **Keep** if used — `java.util.Optional` is fine. Do not swap for nulls. |

### Definition of Done for the port copy

```bash
# Core engine still compiles at Java 11
./gradlew :core-engine:compileJava

# No records survive
grep -rn "record " core-engine/src     # must be empty

# No jspecify
grep -rn "jspecify" core-engine/src    # must be empty

# No Android imports in the core
grep -rn "import android\." core-engine/src   # must be empty

# No forbidden desktop APIs
grep -rn "Thread.ofVirtual\|java.net.http\|ProcessHandle\|ProcessBuilder\|java.nio.file.Path" core-engine/src   # must be empty
```

All five checks must pass before proceeding.

---

## 2. COPY: The `RiskLevel` Enum and Default Mapping (`engine-cli`)

A small, dependency-free class that directly implements the approval policy your `:security-interface` needs. Locate it, verify it has no imports beyond `java.*`, then copy.

### Expected shape (verify against the source)

```java
public enum RiskLevel {
    L0, L1, L2, L3, L4;

    public boolean requiresApproval() {
        return this.compareTo(L3) >= 0;
    }
}
```

### Default risk mapping to seed in `:security-interface`

| Tool class | Risk | Approval |
| :--- | :--- | :--- |
| `read_file`, `list_files` | L0 | No |
| `write_file` (inside app dir) | L1 | No |
| `write_file` (outside app dir) | L2 | No |
| `http_get` | L2 | No |
| `http_post` (to non-allowlisted host) | L3 | **Yes** |
| `run_command` | L4 | **Yes (mandatory)** |
| Unknown tool | L3 | **Yes (default)** |

### Copy procedure

1. Place the enum in `security-interface/src/main/java/.../policy/RiskLevel.java`.
2. Add a `ToolRiskRegistry` (new code, not copied) mapping each `ToolWorker` type to a `RiskLevel`.
3. Wire the registry into the Interlock Relay approval gate in **Phase D** — every `ToolExecutorPort.execute()` call consults the registry before dispatch.

### Definition of Done

- `RiskLevel.requiresApproval()` returns `false` for L0–L2 and `true` for L3–L4.
- Unknown tools resolve to L3 by default (unit test this).
- Denying an L4 tool returns a structured error to the ReAct loop without crashing it.

---

## 3. ADAPT: The Provider Configuration Pattern (`engine-llm-http`)

**Do not copy the implementation.** `ProviderConfigLoader` uses `java.net.http` and environment-variable precedence that is desktop-centric. **Copy the specification** — the priority chain, default base URLs, and separation of config from transport — into your Phase A `ProviderRepository` and `ProviderPresets`.

### Expected source behavior (verify on clone)

The loader resolves provider config in priority order:

1. `APEX_LLM_BASE_URL` + `APEX_LLM_API_KEY` (+ optional `APEX_LLM_MODEL`, `APEX_LLM_TIMEOUT`)
2. `OPENROUTER_API_KEY` → base URL defaults to `https://openrouter.ai/api/v1`
3. `NVIDIA_API_KEY` → `https://integrate.api.nvidia.com/v1`
4. `DEEPSEEK_API_KEY` → `https://api.deepseek.com/v1`
5. `GROQ_API_KEY` → `https://api.groq.com/openai/v1`

### Android adaptation rules

| Source concept | Android replacement | Where it lives |
| :--- | :--- | :--- |
| Environment-variable precedence | **DataStore** priority + explicit user selection in Settings | `app/data/provider/ProviderRepository.kt` |
| API key from env | **Android Keystore**, fetched at call time via `SecretsPort` | `app/data/provider/ProviderRepository.kt` |
| Base URL defaults | **Preset dropdown** in provider editor (see below) | `app/data/provider/ProviderPresets.kt` |
| `java.net.http` transport | `HttpURLConnection` in `OpenAiCompatibleClient` | `core-engine/.../client/` |
| Timeout from env | Per-provider timeout field in `ProviderConfig` (default 60s) | `app/data/provider/ProviderConfig.kt` |

### Preset list to ship (reconcile with the main instructions' list)

```
OpenAI            https://api.openai.com/v1
OpenRouter        https://openrouter.ai/api/v1
Groq              https://api.groq.com/openai/v1
DeepSeek          https://api.deepseek.com/v1          ← add (from Apex source)
Gemini (compat)   https://generativelanguage.googleapis.com/v1beta/openai
NVIDIA NIM        https://integrate.api.nvidia.com/v1
Ollama (local)    http://localhost:11434/v1
LM Studio (local) http://localhost:1234/v1
Custom            (blank)
```

**Note:** the main instructions list should be treated as the **authoritative** preset set. The Apex source adds **DeepSeek**; include it. Do not add presets for providers not present in either source.

### Definition of Done

- Preset dropdown auto-fills `baseUrl` and remains editable.
- Priority resolution: user-selected active provider wins over any seeded default.
- No API key is read from environment or `BuildConfig` at runtime; Keystore is the only source after first launch.

---

## 4. REFERENCE ONLY: Do Not Copy These

Read these to inform implementation; do not paste them into `:core-engine` or `:app`.

### 4.1 The ReAct runner (`engine-cli`)

Read `ReAct*.java` (both stub and real variants) **before** porting Leanarch2's loop in **Phase B**. Compare the two loops and note:

- Where each iteration checks for cancellation (relevant to the Android `Future.cancel` port).
- How it invokes `ToolExecutorPort.execute()` (this is where `RiskLevel` and `ApprovalPort` plug in).
- How it feeds tool results back into the planner.

Then implement the loop inside `:core-engine` using the ported Leanarch2 code. **Do not copy the runner** — it depends on SnakeYAML and `java.net.http` adapters.

### 4.2 The audit hash-chain (`AuditPort` / `AuditEntry`)

`AuditPort` owns a chain with `previousHash` on each entry. This is a good tamper-evident model. Implement the same **concept** in `:security-interface`:

- Each audit entry stores `{timestamp, toolName, riskLevel, decision, argumentsHash, previousHash, entryHash}`.
- `entryHash = SHA-256(canonical(entry minus entryHash) + previousHash)`.
- Persist to app-private storage (`getFilesDir()/audit/`) as an append-only file or Room table.

Do **not** copy the Apex implementation — it is bound to enterprise storage adapters.

### 4.3 The planner (`engine-planner`)

An A* / GOAP planner with OODA replanning. **Out of scope** for a lean Android MVP. Skim it only to understand *when* replanning is triggered. Do not attempt to port it in Phases A–E. If planning sophistication is needed later, revisit.

---

## 5. DISCARD: Do Not Compile, Do Not Vendor

| Module | Reason |
| :--- | :--- |
| `engine-llm-http` implementation | `java.net.http` — not available on Android. Replaced by `OpenAiCompatibleClient`. |
| `engine-cli` | SnakeYAML + desktop CLI entry point. Task schema is reference only. |
| `apex-platform` (audit, auth, tenant) | Spring-oriented, enterprise RBAC/JWT/multi-tenancy — irrelevant for single-user Android. Would drag Spring into the APK. |
| `apex` aggregator | Maven aggregator module, no application code. |
| Any `pom.xml` | Project uses Gradle. Do not import Maven build files. |

If you find yourself needing something from a discarded module, **add the interface to `:core-engine`'s port catalogue or the `:security-interface` SPI** instead — preserve the dependency chain.

---

## 6. Integration With the Existing Phase Plan

| Phase | What this document changes |
| :--- | :--- |
| **Phase A** — Provider config | Add **DeepSeek** preset; follow the priority-chain *pattern* from `ProviderConfigLoader`, not its code. Presets and Keystore rules are unchanged. |
| **Phase B** — Port Leanarch2 core | **Copy** the nine port interfaces into `core-engine/.../port/` (with the edits above). Convert records to final classes. **Read** `engine-cli`'s ReAct runner as a reference before porting the loop. |
| **Phase C** — Swap transport | No change. The ported `ChatModelPort` is implemented by `OpenAiCompatibleClient`. |
| **Phase D** — Security interface | **Copy** `RiskLevel` + default mapping into `security-interface/.../policy/`. Add `ToolRiskRegistry`. Wire into Interlock Relay's approval gate. Implement the hash-chain audit **concept** (not the Apex code) in the audit log. |
| **Phase E** — Agent mode | No change. |

---

## 7. Verification Additions

Add these to the per-phase verification checklist in the main instructions:

```bash
# No records in core (Java 11 requirement)
grep -rn "record " core-engine/src                       # must be empty

# No jspecify anywhere
grep -rn "jspecify" core-engine/src security-interface/src   # must be empty

# No Maven POMs leaked into the Gradle project
find . -name "pom.xml" -not -path "./.gradle/*"          # must be empty

# RiskLevel default is L3 for unknown tools (unit test)
./gradlew :security-interface:test --tests "*ToolRiskRegistry*"

# Provider preset list matches the reconciled set (no drift)
grep -rn "api.deepseek.com" app/src                     # must be present
```

---

## 8. License and Attribution

If any code is **copied** (the port interfaces, the `RiskLevel` enum), you must:

1. Confirm the Apex repository's `LICENSE` permits it.
2. Preserve any copyright headers present in the copied files.
3. Add an attribution note in `NOTICE` or `THIRD_PARTY_NOTICES.md` at the project root, listing the source repository, commit hash, and files copied.

If the license is incompatible or absent, **do not copy** — rewrite the interfaces from the concepts in this document (the catalogue and the risk table above are sufficient to reproduce them independently) and report the rewrite in the phase report.

---

## 9. Reporting Additions

For any phase that touches this document, extend the standard phase report with:

1. **Copied files:** path in Apex source → path in project, commit hash, license status.
2. **Adapted files:** what was changed and why (especially record→class conversions).
3. **Reference-only reads:** which files you read for architecture, with one-line takeaways.
4. **Discarded modules confirmed untouched:** confirm no `apex-platform`, no `engine-llm-http` implementation, no `pom.xml` in the build.

Do not claim a copy is "clean" unless the license check passed and the attribution is in place.