# 01 — Immediate Foundation: Tests, JSON Portability, Audit Persistence, CI

> **Document type:** Instructional / Revision Plan
> **Target repo:** `jcham2024-arch/Google-Ai-repo`
> **Scope:** First 1–2 weeks of remediation
> **Owner:** Project lead (you)
> **Prerequisite reading:** Repo `README.md`, module `build.gradle.kts` files

---

## 0. Purpose

The review found four blocking issues that must be resolved before any feature work
continues:

1. **Zero test coverage** — the "production-grade" claim is unverifiable.
2. **Hand-rolled `MiniJson`** — regex-based parsing will fail on real LLM payloads.
3. **In-memory `AuditLogManager`** — a tamper-evident trail that evaporates on restart
   is not an audit trail.
4. **No CI** — nothing prevents regressions or rule violations.

This document is a step-by-step instruction set for closing all four. Work the tasks
**in order**; each task ends with a Definition of Done (DoD) checklist.

---

## 1. Non-negotiable Constraints (carry these through every task)

| # | Constraint | Enforcement |
|---|-----------|-------------|
| C1 | Dependency direction stays `:app` → `:security-interface` → `:core-engine` | CI grep (Task 4) |
| C2 | `:core-engine` has **no third-party runtime dependencies** and **no Android imports** | CI grep (Task 4) |
| C3 | Ports live in `:core-engine`; implementations live in `:security-interface` or `:app` | Code review |
| C4 | Every new public type gets at least one unit test | Coverage gate (Task 1) |
| C5 | No secrets, keys, or tokens in source | gitleaks in CI (Task 4) |

> **Architectural note:** Constraint C2 is *not* being abandoned in this plan. Instead,
> we move the brittleness (JSON parsing) **behind a port** so the well-tested
> implementation can live in a module that is allowed dependencies. This preserves the
> project's design philosophy while removing the liability.

---

## 2. Task 1 — Establish the Testing Foundation

### 2.1 Goal
Create the missing test source sets, a shared test-fixture module usage pattern, and
cover the three highest-risk classes: `SecurityInterlock`, `AuditLogManager`, and the
port data models.

### 2.2 Create test source sets

For **each** module (`:core-engine`, `:security-interface`, `:app`), ensure the
directory layout exists:

```
core-engine/src/test/kotlin/...
security-interface/src/test/kotlin/...
app/src/test/kotlin/...
```

If any module's `build.gradle.kts` does not already declare JUnit, add it:

```kotlin
// core-engine/build.gradle.kts
dependencies {
    testImplementation(libs.junit)          // already present per review
    testImplementation(kotlin("test"))
}
```

For `:security-interface`, add the mocking library you will standardize on (pick one,
do not mix):

```kotlin
// security-interface/build.gradle.kts
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.mockk)          // or mockito-kotlin — choose ONE
    testImplementation(kotlin("test"))
}
```

### 2.3 Write the first five test classes (required)

#### (a) `SecurityInterlockTest` — risk classification
File: `security-interface/src/test/kotlin/.../SecurityInterlockTest.kt`

Cover, at minimum:

```kotlin
@Test fun `read-only tool is classified L0 or L1`() { /* ... */ }

@Test fun `tool touching local files is at least L2`() { /* ... */ }

@Test fun `destructive or network-egress tool requires L3 plus approval`() { /* ... */ }

@Test fun `unknown risk level fails closed (deny)`() { /* ... */ }

@Test fun `null or blank tool id is rejected`() { /* ... */ }
```

> **Why the last one matters:** the review found null checks but no evidence of a
> fail-closed default. This test pins that behavior permanently.

#### (b) `AuditLogManagerTest` — hash-chain integrity
File: `security-interface/src/test/kotlin/.../AuditLogManagerTest.kt`

```kotlin
@Test fun `appending an entry links it to the previous hash`() { /* ... */ }

@Test fun `verifying an untampered trail succeeds`() { /* ... */ }

@Test fun `mutating a middle entry breaks verification`() { /* ... */ }

@Test fun `empty trail verifies successfully`() { /* ... */ }

@Test fun `trail survives a simulated restart`() {
    // Pending Task 3 — mark @Disabled("enable after Task 3") until persistence lands
}
```

#### (c) `MiniJsonTest` — document current behavior before changing it
File: `core-engine/src/test/kotlin/.../MiniJsonTest.kt`

Write **characterization tests** that lock in what `MiniJson` does today, including
the cases you suspect are broken. Example table:

| Input | Expected today | Desired (Task 2) |
|-------|----------------|------------------|
| `{"a":"b"}` | parses | parses |
| `{"a":"b\"c"}` | *probably breaks* | parses |
| `{"a":{"b":1}}` | *probably breaks* | parses |
| `{"a":"\u00e9"}` | *probably breaks* | parses |
| `[1,2,3]` | *unknown* | parses |

Tests that fail today should be annotated `@Disabled` with a comment referencing
**Task 2**, so the suite stays green while the gap is documented.

#### (d) `ChatModelPortModelTest` — port data models
File: `core-engine/src/test/kotlin/.../ChatModelPortModelTest.kt`

Assert invariants: non-blank message roles, message ordering preserved, tool-call
argument strings round-trip.

#### (e) `ApprovalPortContractTest` — contract test
A reusable abstract contract class that **both** the real implementation and the fake
must extend:

```kotlin
abstract class ApprovalPortContractTest {
    abstract fun createPort(): ApprovalPort

    @Test fun `deny is the default when no decision is made`() { /* ... */ }
    @Test fun `approval is scoped to a single request id`() { /* ... */ }
}
```

This is the pattern that keeps future adapters honest.

### 2.4 Coverage gate

Add JaCoCo to the root build:

```kotlin
// build.gradle.kts (root)
plugins {
    jacoco
}
subprojects {
    apply(plugin = "jacoco")
    tasks.withType<Test> { finalizedBy(tasks.withType<JacocoReport>()) }
    tasks.withType<JacocoCoverageVerification> {
        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    minimum = "0.60".toBigDecimal()
                }
            }
        }
    }
}
```

> Start at **60% lines on `:security-interface` and `:core-engine` only**. Do not gate
> `:app` yet — UI wiring coverage is low-value at this stage. Raise the gate by 5 points
> per month until it reaches 80%.

### 2.5 Task 1 — Definition of Done

- [ ] `src/test/kotlin` exists in all three modules
- [ ] The five test classes above exist and compile
- [ ] `./gradlew test` runs green locally and in CI (CI lands in Task 4)
- [ ] Failing "desired behavior" tests are `@Disabled` with a `TODO(Task 2)` / `TODO(Task 3)` tag
- [ ] JaCoCo report generated; ≥60% line coverage on the two library modules
- [ ] A short `docs/testing.md` explains how to run tests and where fixtures live

---

## 3. Task 2 — Replace Brittle JSON Behind a Port

### 3.1 Goal
Remove `MiniJson` from the critical path without violating Constraint C2.

### 3.2 Step 1 — Define the port (in `:core-engine`)

```kotlin
// core-engine/src/main/kotlin/.../port/JsonCodecPort.kt
package ...port

/**
 * Minimal JSON boundary used by the engine to talk to model APIs and tool schemas.
 * Implementations must be lossless for UTF-8, escapes, nesting, and arrays.
 */
interface JsonCodecPort {
    fun encodeToString(value: Map<String, Any?>): String
    fun encodeToString(value: List<Any?>): String
    fun decodeObject(json: String): Map<String, Any?>
    fun decodeArray(json: String): List<Any?>
}
```

Keep `MiniJson` as the **default fallback** implementation inside `:core-engine` so the
module still builds standalone — but nothing in production wiring should use it.

### 3.3 Step 2 — Choose an implementation (decision record required)

Create `docs/adr/0002-json-codec.md` and pick **one**:

| Option | Module allowed | Pros | Cons |
|--------|----------------|------|------|
| `kotlinx.serialization` | `:app` (and `:security-interface`) | Compile-time safety, Kotlin-native, streaming-friendly | Adds plugin to app only |
| `Gson` | `:app` | Tiny, battle-tested | Reflection, no Kotlin null-safety |
| `org.json` | `:app` | Smallest footprint | Weak typing |

**Recommendation:** `kotlinx.serialization`. It is Kotlin-idiomatic, and because the
codec is behind a port the engine never sees it.

### 3.4 Step 3 — Implement the adapter

```kotlin
// app/src/main/kotlin/.../adapter/KotlinxJsonCodec.kt
class KotlinxJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = false }
) : JsonCodecPort {
    override fun encodeToString(value: Map<String, Any?>): String =
        json.encodeToString(JsonObject.serializer(), value.toJsonElement()).toString()
    // ...decode methods using json.parseToJsonElement(...)
}
```

### 3.5 Step 4 — Migrate call sites

1. `OpenAiCompatibleClient` (or its successor) takes `JsonCodecPort` via constructor.
2. Tool schema generation uses `JsonCodecPort.encodeToString`.
3. Delete direct `MiniJson` imports from `:security-interface` and `:app`.
4. `grep -r "MiniJson" --include=*.kt` must return **only** `core-engine/src/main`
   (the fallback) and `core-engine/src/test` (characterization tests).

### 3.6 Step 5 — Turn the characterization tests green

Un-`@Disable` the `MiniJsonTest` cases from Task 1 **against the port**, not against
`MiniJson` directly. Add a `JsonCodecPortContractTest` abstract class and run it against
both `MiniJson` (marking known failures as expected) and `KotlinxJsonCodec` (all green).

### 3.7 Task 2 — Definition of Done

- [ ] `JsonCodecPort` exists in `:core-engine` with KDoc
- [ ] `docs/adr/0002-json-codec.md` merged with a decision and rationale
- [ ] Adapter implemented in `:app`, injected into the HTTP client
- [ ] No production call site imports `MiniJson`
- [ ] `JsonCodecPortContractTest` passes against the chosen adapter
- [ ] CI still proves `:core-engine` has no third-party runtime deps

---

## 4. Task 3 — Persist the Audit Trail

### 4.1 Goal
Make the hash-chained audit trail durable, append-only, verifiable after restart, and
rotatable without breaking the chain.

### 4.2 Step 1 — Define the port

```kotlin
// core-engine/src/main/kotlin/.../port/AuditStorePort.kt
interface AuditStorePort {
    /** Append a record; returns the assigned sequence number. */
    fun append(record: AuditRecord): Long
    /** Read all records in sequence order, oldest first. */
    fun readAll(): Sequence<AuditRecord>
    /** Verify the chain from genesis; returns the first broken index or null if intact. */
    fun verify(): Long?
}

data class AuditRecord(
    val seq: Long,
    val timestampEpochMs: Long,
    val prevHash: String,   // "GENESIS" for seq 0
    val payloadJson: String,
    val hash: String        // sha256(prevHash + timestamp + payloadJson)
)
```

> Move the hash computation into a single `AuditHasher` value object so the format is
> defined in exactly one place. The existing `AuditLogManager` should delegate to the
> port; keep it as a thin façade so current call sites do not change.

### 4.3 Step 2 — Implement the file-backed store (`:security-interface`)

Format: **newline-delimited JSON (JSONL)**, one record per line, append-only.

```
{"seq":0,"ts":1730000000000,"prev":"GENESIS","payload":"{...}","hash":"ab12..."}
{"seq":1,"ts":1730000000123,"prev":"ab12...","payload":"{...}","hash":"cd34..."}
```

Implementation rules:

1. Open with `FileChannel` + `StandardOpenOption.APPEND`, **not** `FileOutputStream`.
2. `fsync` (or `channel.force(true)`) after every append before returning.
3. Validate `prevHash` equals the last record's hash **before** writing; throw
   `AuditChainBrokenException` otherwise.
4. `verify()` streams the file, recomputes each hash, returns first mismatch.
5. Never rewrite history. Rotation = close file, start `audit-<timestamp>.jsonl`,
   first record's `prevHash` = last hash of prior file.

### 4.4 Step 3 — Wire it up

- In `:app` bootstrap, construct `FileAuditStore(File(context.filesDir, "audit/"))`
  (or a configurable path on desktop/JVM).
- Replace the `ArrayList` in `AuditLogManager` with `AuditStorePort`.
- Add a **startup self-check**: `store.verify()`; if non-null, log CRITICAL and refuse
  to start unless an explicit `--allow-broken-audit` flag is set. Fail closed.

### 4.5 Step 4 — Prove it

Un-`@Disable` the "survives restart" test from Task 1:

```kotlin
@Test fun `trail survives a simulated restart`() {
    val dir = createTempDir()
    FileAuditStore(dir).use { it.append(record("a")); it.append(record("b")) }
    val reopened = FileAuditStore(dir)
    assertEquals(2, reopened.readAll().count())
    assertNull(reopened.verify())
}
```

Add a rotation test: rotate, append, verify across both files.

### 4.6 Task 3 — Definition of Done

- [ ] `AuditStorePort` + `AuditRecord` in `:core-engine`
- [ ] `FileAuditStore` in `:security-interface` with append, readAll, verify
- [ ] `AuditLogManager` delegates; no `ArrayList` remains in the audit path
- [ ] Restart and tamper tests pass
- [ ] Rotation documented in `docs/audit-format.md` with the exact hash formula
- [ ] Startup fails closed on a broken chain

---

## 5. Task 4 — Add CI and Enforce the Rules

### 5.1 Goal
Every push and PR runs tests, the dependency-direction guards, and a secret scan.

### 5.2 Workflow file

```yaml
# .github/workflows/ci.yml
name: CI
on:
  push: { branches: [ main ] }
  pull_request:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v3
      - name: Test + coverage gate
        run: ./gradlew test jacocoTestCoverageVerification
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          path: '**/build/reports/tests/**'

  guardrails:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: core-engine must stay dependency-free
        run: |
          set -e
          if grep -nE '^\s*(implementation|api)\(' core-engine/build.gradle.kts; then
            echo "::error file=core-engine/build.gradle.kts::core-engine must not declare runtime deps"
            exit 1
          fi
      - name: core-engine must not import Android
        run: |
          if grep -rn '^import android\.' core-engine/src/main; then
            echo "::error::Android import leaked into core-engine"; exit 1
          fi
      - name: no production MiniJson usage outside core-engine
        run: |
          if grep -rn 'MiniJson' app/src security-interface/src; then
            echo "::error::MiniJson used outside core-engine"; exit 1
          fi
      - name: dependency direction
        run: |
          if grep -q 'project(":app")' core-engine/build.gradle.kts security-interface/build.gradle.kts; then
            echo "::error::reverse dependency detected"; exit 1
          fi

  secrets:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: gitleaks/gitleaks-action@v2
        env: { GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }} }
```

### 5.3 Branch protection

On GitHub: **Settings → Branches → Protect `main`**

- Require status checks: `build`, `guardrails`, `secrets`
- Require 1 approving review
- Require branches to be up to date before merging
- Disallow force pushes

### 5.4 Task 4 — Definition of Done

- [ ] `ci.yml` merged and green on `main`
- [ ] Branch protection active with required checks
- [ ] A deliberately broken PR (e.g., adding `implementation("gson")` to `core-engine`)
      is **blocked** — verify once, then revert
- [ ] `docs/ci.md` lists every guardrail and *why* it exists

---

## 6. Sequencing & Effort Estimate

| Order | Task | Est. effort | Depends on |
|-------|------|-------------|-----------|
| 1 | Testing foundation | 3–4 days | — |
| 2 | JSON port + adapter | 2–3 days | Task 1 (tests) |
| 3 | Audit persistence | 2–3 days | Task 1 (tests) |
| 4 | CI + guardrails | 1–1.5 days | Task 1 (green suite) |

Total: **~9–12 working days** for one focused engineer.

> Tasks 2 and 3 can run in parallel once Task 1 is done. Task 4 should land as early as
> possible after Task 1 — even before 2 and 3 finish — so the guards start protecting
> the work.

---

## 7. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Test suite exposes many latent bugs | High | Timebox fixing to the three critical classes; file issues for the rest |
| Team resists the JSON port as "over-abstraction" | Medium | Show the failing `MiniJson` cases from Task 1 as the evidence |
| File-based audit is too slow at high volume | Low (early) | Keep `AuditStorePort`; batch writes later without changing call sites |
| Coverage gate blocks unrelated PRs | Medium | Start at 60%, exclude generated code, raise slowly |

---

## 8. Exit Criteria for This Document

All four DoD sections are complete **and**:

- `./gradlew test` is green with ≥60% coverage on the two library modules
- A tampered audit trail is detected after restart
- An LLM response containing nested tool calls round-trips through the codec unchanged
- A PR that violates C1–C5 is automatically blocked

Proceed to **`02-short-term-hardening.md`** once these exit criteria are met.