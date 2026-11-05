# 03 — Long-Term Production Readiness: Scale, Operations, Process, and Sustainability

> **Document type:** Instructional / Production Readiness Plan
> **Target repo:** `jcham2024-arch/Google-Ai-repo`
> **Scope:** Months 3–6 after `02-short-term-hardening.md` completes
> **Owner:** Project lead (you) + one operations engineer
> **Prerequisite:** All exit criteria from `02-short-term-hardening.md` are met.
> HTTP is resilient, telemetry is live, security is fail-closed and reviewed against a
> threat model, and the E2E harness proves the pipeline end-to-end.

---

## 0. Purpose

Documents 01 and 02 made the project **trustworthy** and **operable**. This document
makes it **sustainable**: it can be deployed, scaled, upgraded, and handed to a team
without the original author present. It also resolves the one open architectural
question the review flagged — the "zero third-party dependencies" policy for
`:core-engine` — with a written decision rather than by drift.

Five tasks. Each ends with a Definition of Done. Work them **in order** — Task 5
(governance) codifies what Tasks 1–4 establish, and cannot be written honestly until
the others are real.

---

## 1. Constraints Carried Forward

All constraints from Documents 01 and 02 remain in force:

| # | Constraint |
|---|-----------|
| C1 | Dependency direction stays `:app` → `:security-interface` → `:core-engine` |
| C2 | `:core-engine` has no third-party runtime dependencies and no Android imports |
| C3 | Ports live in `:core-engine`; implementations live in `:security-interface` or `:app` |
| C4 | Every new public type gets at least one unit test |
| C5 | No secrets in source |
| C6 | Every network call goes through `HttpTransportPort` |
| C7 | Every tool invocation emits a span + log + counter + histogram |
| C8 | No `println` / `printStackTrace` / `System.out` in any module |
| C9 | A failing `TelemetryPort` never crashes the engine |

**New constraints added by this document:**

| # | Constraint | Enforcement |
|---|-----------|-------------|
| C10 | Every deployment ships a signed, versioned artifact | Release workflow (Task 3) |
| C11 | Every operational state has a written runbook | Docs review (Task 4) |
| C12 | Every breaking change ships with a migration note and a deprecated-path window | Changelog check (Task 5) |
| C13 | `:core-engine` dependency policy is governed by `docs/adr/0003-core-engine-dependencies.md` | ADR review (Task 1) |

---

## 2. Task 1 — Resolve the Zero-Dependency Policy (ADR)

### 2.1 Why this task comes first

The review identified the hand-rolled JSON, HTTP, and audit code as consequences of an
**unwritten** rule ("`:core-engine` must have zero third-party dependencies"). Documents
01 and 02 routed around the rule via ports, which was correct — but left the rule itself
unexamined. Left alone, the rule will be re-litigated by every future contributor. Fix
it in writing now, before Task 5 asks new contributors to follow it.

### 2.2 Write `docs/adr/0003-core-engine-dependencies.md`

The ADR **must** answer these questions explicitly:

1. **What problem does the rule actually solve?** (Candidate answers: portability,
   auditability, supply-chain risk, Android/JVM dual-target support. Name the real one.)
2. **What does the rule cost?** (Every utility gets reimplemented; bugs in `MiniJson`
   and `AuditLogManager` are the evidence.)
3. **What alternatives were considered?**
   - A. Status quo (zero deps)
   - B. Allowlist of vetted small libraries
   - C. Full third-party allowed
4. **What is the decision, and what is the trigger to revisit it?**

### 2.3 Recommended decision

**Adopt option B — a vetted allowlist — with a strict process:**

- A library may be added to `:core-engine` only if it satisfies **all** of:
  - Published under Apache-2.0, MIT, or BSD.
  - No transitive runtime dependencies (verify with `./gradlew :core-engine:dependencies`).
  - ≥1.0.0 release and ≥2 years of maintained history.
  - Passes `gitleaks`, `ossf/scorecard` (≥7.0), and a manual review recorded in the ADR.
- The allowlist lives in a single file `core-engine/allowed-deps.txt` and is checked in CI.
- Every addition is an ADR amendment. No exceptions.

> **Rationale:** The zero-dependency rule has already cost the project one brittle JSON
> parser and one non-persistent audit trail. A vetted allowlist keeps the supply-chain
> property that motivated the rule while removing the reimplementation tax. If the team
> rejects option B in the ADR, that is a valid outcome — but it must be a **written**
> decision with a stated rationale.

### 2.4 Update CI guardrails (from Doc 01)

Replace the blunt "no runtime deps" grep with an allowlist check:

```bash
# .github/workflows/ci.yml — guardrails job, replaces the old core-engine check
- name: core-engine dependency allowlist
  run: |
    set -euo pipefail
    deps=$(grep -oP '(?<=implementation\(").*(?="\))' core-engine/build.gradle.kts || true)
    while IFS= read -r dep; do
      [ -z "$dep" ] && continue
      if ! grep -qxF "$dep" core-engine/allowed-deps.txt; then
        echo "::error file=core-engine/build.gradle.kts::'$dep' not in allowed-deps.txt. Add via ADR amendment."
        exit 1
      fi
    done <<< "$deps"
```

### 2.5 Task 1 — Definition of Done

- [ ] `docs/adr/0003-core-engine-dependencies.md` merged with an explicit decision
- [ ] `core-engine/allowed-deps.txt` exists (may be empty if option A chosen)
- [ ] CI guardrail updated to enforce the ADR's choice
- [ ] `README.md` links to the ADR from the architecture section
- [ ] The rule is restated once, canonically, in the ADR — not scattered across comments

---

## 3. Task 2 — Scalability and Graceful Degradation

### 3.1 Goal
Make the system behave predictably under concurrency, load, and partial failure. The
review found no evidence of thread-pool management, rate limiting, or degradation paths.

### 3.2 Step 1 — Concurrency model

Document the threading model in `docs/scalability.md` **before** changing code. Answer:

1. Which dispatchers exist and what work runs on each?
   (e.g., `Dispatchers.IO` for network, a bounded `Dispatchers.Default` for tool
   execution, the main thread never blocks on `HttpTransportPort`)
2. What is the maximum in-flight model call count? (Name a number.)
3. What is the maximum in-flight tool execution count? (Name a number.)
4. What happens when those limits are reached? (Queue with timeout, or reject — pick
   one, document it.)

### 3.3 Step 2 — Bound everything

Every unbounded queue, pool, or buffer in the codebase must be given an explicit bound.
Grep to find them, then fix each:

```bash
grep -rn "ArrayList()\|mutableListOf()\|LinkedBlockingQueue()\|Executors\.newCachedThreadPool\|CoroutineScope(Dispatchers" \
  --include=*.kt core-engine/src security-interface/src app/src
```

For each hit, either bound it or document why it is safe to be unbounded.

### 3.4 Step 3 — Rate limiting

Add a token-bucket `RateLimiterPort` in `:core-engine`, implemented in `:app`, applied
at two boundaries:

1. **Outbound to the LLM provider** — respects the provider's published rate limit.
   A `429` from the provider is a **failure of this limiter**, not a normal event.
2. **Inbound per user/session** — the number of agent turns a single session may
   initiate per minute. Prevents a runaway loop from exhausting the provider budget.

```kotlin
// core-engine/src/main/kotlin/.../port/RateLimiterPort.kt
interface RateLimiterPort {
    /** Suspends until a permit is available, or throws on timeout. */
    suspend fun acquire(bucket: String, permits: Int = 1)
}
```

Both boundaries get metrics (from Doc 02's `TelemetryPort`): `ratelimit.acquired`,
`ratelimit.throttled`, `ratelimit.timeout`.

### 3.5 Step 4 — Graceful degradation paths

Define and implement three states in `docs/runbooks/degradation.md`:

| State | Trigger | Behavior |
|-------|---------|----------|
| **Normal** | Default | Full functionality |
| **Degraded** | Provider error rate > 10% over 5 min, or telemetry exporter down > 2 min | Model calls continue; new sessions rejected with a clear error; telemetry falls back to local file |
| **Safe mode** | Audit store write failures, or security interlock unable to reach `ApprovalPort` | Only L0/L1 tools allowed; all L2+ denied with a logged reason |

The transition between states is **automatic** where possible, and always **logged with
a metric** (`system.state` gauge). Document who gets paged in each transition.

### 3.6 Step 5 — Load test

Add a `app/src/test/kotlin/.../load/LoadSmokeTest.kt` — not a full benchmark, but a
smoke test that asserts:

- 100 concurrent tool invocations complete without deadlock
- The rate limiter caps throughput at the configured bound
- Degraded state is entered when the mock provider fails 20% of calls

This test **must not** be part of the default `test` task (it is slow). Wire it into
the `e2e` job from Doc 02 as a nightly run.

### 3.7 Task 2 — Definition of Done

- [ ] `docs/scalability.md` documents the threading model with concrete numbers
- [ ] Every unbounded queue/buffer either bounded or documented as intentionally so
- [ ] `RateLimiterPort` enforced at both boundaries, with metrics
- [ ] Degraded and safe-mode states implemented and tested
- [ ] `system.state` metric emitted on every transition
- [ ] Load smoke test passes nightly in CI
- [ ] `docs/runbooks/degradation.md` lists page triggers and operator actions

---

## 4. Task 3 — Release Engineering

### 4.1 Goal
Produce signed, versioned, reproducible artifacts. The review found no release pipeline,
no changelog, no versioning discipline.

### 4.2 Step 1 — Versioning

Adopt **Semantic Versioning**, with pre-1.0 semantics defined explicitly:

- `0.x.y` — the port interfaces may change between minor versions, with a migration
  note (C12) and a deprecation window of at least one minor version.
- `1.0.0` is declared only after Task 5 completes (stable contribution process exists).
- Version is derived from a git tag `vX.Y.Z`; the tag is the source of truth. Never
  commit a version number by hand.

Add to `build.gradle.kts`:

```kotlin
allprojects {
    version = System.getenv("RELEASE_VERSION") ?: "0.0.0-SNAPSHOT"
}
```

### 4.3 Step 2 — Changelog discipline

Adopt `Keep a Changelog` format at `CHANGELOG.md`. Every PR that changes behavior or a
port interface **must** add an entry in an `Unreleased` section. CI checks this:

```yaml
- name: changelog entry present
  if: github.event_name == 'pull_request'
  run: |
    if ! git diff --name-only "origin/${{ github.base_ref }}"...HEAD | grep -qxF CHANGELOG.md; then
      if ! git diff "origin/${{ github.base_ref }}"...HEAD -- '*.kt' | grep -q '^[+-]' ; then
        echo "No Kotlin changes — changelog entry not required."; exit 0
      fi
      echo "::error file=CHANGELOG.md::Source changed but no CHANGELOG.md entry. Add one under 'Unreleased'."
      exit 1
    fi
```

### 4.4 Step 3 — Release workflow

```yaml
# .github/workflows/release.yml
name: Release
on:
  push:
    tags: [ 'v*.*.*' ]
jobs:
  release:
    runs-on: ubuntu-latest
    permissions:
      contents: write
      packages: write
      id-token: write        # for sigstore cosign
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v3
      - name: Verify tag matches version
        run: |
          tag="${GITHUB_REF#refs/tags/v}"
          grep -q "## \\[$tag\\]" CHANGELOG.md || {
            echo "::error::CHANGELOG.md has no section for $tag"; exit 1; }
      - name: Build artifacts
        run: RELEASE_VERSION="${GITHUB_REF#refs/tags/v}" ./gradlew clean build -Prelease
      - name: Sign artifacts
        uses: sigstore/cosign-installer@v3
      - run: |
          for f in app/build/libs/*.jar; do
            cosign sign-blob --yes --output-signature "$f.sig" "$f"
          done
      - name: Publish GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            app/build/libs/*.jar
            app/build/libs/*.jar.sig
```

### 4.5 Step 4 — Reproducible builds

Add a `gradle.properties` pin:

```properties
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
```

And commit the Gradle wrapper checksum to `gradle/wrapper/gradle-wrapper.properties`
(the `distributionSha256Sum` field). A build run twice from the same tag must produce
byte-identical jars — assert this in a post-release check.

### 4.6 Task 3 — Definition of Done

- [ ] SemVer policy documented in `docs/versioning.md`
- [ ] `CHANGELOG.md` present with `Keep a Changelog` structure
- [ ] CI enforces a changelog entry on every source-changing PR
- [ ] `release.yml` builds, signs, and publishes on tag push
- [ ] `distributionSha256Sum` pinned; a reproducibility check runs post-release
- [ ] First `v0.1.0` tag published with a complete changelog section

---

## 5. Task 4 — Operational Runbooks

### 5.1 Goal
Every operational state the system can enter has a written runbook. The review found
zero operational documentation.

### 5.2 Required runbooks

Each goes in `docs/runbooks/` and follows the same structure:

```
# Runbook: <name>
## Symptoms
## Impact
## Diagnosis
## Mitigation
## Escalation
## Post-incident
```

The minimum set:

| Runbook | Covers |
|---------|--------|
| `audit-chain-broken.md` | Startup fails closed on broken audit chain (Doc 01). What to do, who approves `--allow-broken-audit`, how to preserve evidence. |
| `llm-provider-outage.md` | Provider returns 5xx or 429 persistently. Enter degraded state, notify users, monitor for recovery. |
| `telemetry-exporter-down.md` | Otel exporter unreachable. Confirm decorator holds (C9), switch to local file fallback, restore exporter. |
| `degradation.md` | Written in Task 2; review and finalize here. |
| `secret-rotation.md` | Rotate LLM API keys, keystore entries, and signing keys. What breaks, what does not. |
| `rollback.md` | A bad release was tagged. How to revert, how to preserve audit trail integrity across the rollback. |

### 5.3 Monitoring and alerting spec

Write `docs/monitoring.md` specifying every alert. Each alert has: **name**, **query**,
**threshold**, **severity**, **runbook link**. Minimum alerts:

- `AuditChainBroken` — severity CRITICAL, runbook `audit-chain-broken.md`
- `SystemStateDegraded` — severity WARN, runbook `llm-provider-outage.md`
- `ToolDenialRateHigh` — severity WARN, links to `docs/security/threat-model.md`
- `TelemetryExportFailing` — severity WARN, runbook `telemetry-exporter-down.md`
- `RateLimitTimeoutRateHigh` — severity WARN, links to `docs/scalability.md`

### 5.4 Task 4 — Definition of Done

- [ ] All six runbooks exist and follow the standard structure
- [ ] `docs/monitoring.md` lists every alert with query, threshold, severity, and runbook link
- [ ] A drill: an engineer who did not write the code executes `audit-chain-broken.md` on a
      deliberately corrupted test trail and confirms the runbook is sufficient — record
      the drill outcome in the runbook itself
- [ ] Every alert links to a runbook that actually exists (CI check: parse the doc, grep the directory)

---

## 6. Task 5 — Contribution Process and Governance

### 6.1 Goal
Make the project sustainable by people who are not the original author. The review
found zero issues, zero PRs, and no review workflow — this task establishes them.

### 6.2 Step 1 — Contribution guide

Write `CONTRIBUTING.md` covering:

1. **Getting set up** — JDK version, Gradle wrapper, how to run tests, how to run the E2E harness.
2. **The rules** — C1–C13, each with a link to the doc that explains *why*.
3. **How to add a tool adapter** — reference `docs/security/tool-adapters.md` checklist (Doc 02).
4. **How to add a port** — reference `docs/adr/0001-port-pattern.md` (create it if it
   does not exist; the pattern is used nine times but never documented).
5. **Review expectations** — one approving review required; architectural changes need
   an ADR; security-relevant changes need a threat-model update (Doc 02).
6. **How to release** — point at `docs/versioning.md` (Task 3).

### 6.3 Step 2 — ADR index and template

- `docs/adr/0000-template.md` — standard ADR template (status, context, decision,
  consequences, alternatives considered).
- `docs/adr/README.md` — index of all ADRs with status and date.
- Backfill the two ADRs already implied: `0001-port-pattern.md` and
  `0002-json-codec.md` (already exists from Doc 01). Confirm `0003` (Task 1) is linked.

### 6.4 Step 3 — Issue and PR templates

- `.github/ISSUE_TEMPLATE/bug_report.md` — repro steps, version, expected vs actual.
- `.github/ISSUE_TEMPLATE/feature_request.md` — problem statement, alternatives, which
  port it would touch.
- `.github/pull_request_template.md` — checklist:

```markdown
- [ ] Tests added for every new public type (C4)
- [ ] CHANGELOG.md updated under "Unreleased" (C12)
- [ ] Threat model updated (if security-relevant — Doc 02)
- [ ] ADR added/amended (if architectural — Task 1)
- [ ] `./gradlew test e2e` passes locally
- [ ] No new deps outside allowed-deps.txt (Task 1)
```

### 6.5 Step 4 — Ownership

- Add a `CODEOWNERS` file mapping `:core-engine` ports to the architect, security paths
  to the security reviewer, and release workflow to the ops engineer.
- Review cadence: **weekly** for issues and PRs, **monthly** to review the ADR index and
  the threat model. Write the cadence into `CONTRIBUTING.md`.

### 6.6 Step 5 — Community hygiene

- Adopt `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1 is standard).
- Adopt a security disclosure policy `SECURITY.md` — private channel for reporting
  vulnerabilities, response time commitment (e.g., 72 hours).
- Enable branch protection on `main` (Doc 01) and on `release/*` branches.

### 6.7 Task 5 — Definition of Done

- [ ] `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md` present and linked from README
- [ ] All three PR/issue templates present
- [ ] `CODEOWNERS` present and enforced by branch protection
- [ ] `docs/adr/` index complete, including backfilled `0001` and `0003`
- [ ] One external contribution landed end-to-end through the process (or, if no
      external contributor is available, a written dry-run recorded in the repo)
- [ ] Review cadence documented and calendared

---

## 7. Sequencing & Effort Estimate

| Order | Task | Est. effort | Depends on |
|-------|------|-------------|-----------|
| 1 | Zero-dependency ADR | 1–2 days | Doc 02 complete |
| 2 | Scalability & degradation | 5–7 days | Task 1 (ADR settles dep policy) |
| 3 | Release engineering | 3–4 days | Tasks 1–2 (artifacts stable) |
| 4 | Operational runbooks | 3–4 days | Task 3 (a release exists to run) |
| 5 | Governance & process | 3–4 days | Tasks 1–4 (process codifies reality) |

Total: **~15–21 working days** for one architect and one operations engineer working
in parallel where the dependency graph allows.

> Task 1 is one or two days and unblocks everything. Do it **first** — do not let the
> dependency question linger into Task 2, where it will affect how rate limiters and
> observability adapters are packaged. Tasks 3 and 4 can overlap. Task 5 must be last:
> governance documents that describe a process nobody is running are worse than no
> documents at all.

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| ADR concludes "keep zero deps", re-empowering the reimplementation tax | Medium | Force the ADR to name the concrete cost (the `MiniJson` bug count) as part of the decision; make option B the default in the template |
| Rate limiter adds latency to the happy path | Low | Measure with the `ToolInvocationInterceptor` histogram; batch token acquisition |
| Degraded-state transitions flap | Medium | Require a 5-minute stable window in both directions before transition; hysteresis is documented |
| Runbooks become shelfware | High | The drill requirement in Task 4 is the anti-shelfware control — treat it as a real acceptance criterion |
| Release signing is skipped "just this once" | Medium | Sign the *tag* workflow, not a manual step; a release without a signature is not a release |
| Governance docs describe a process nobody has ever run | High | The Task 5 DoD requires either a real external contribution or a recorded dry-run — do not accept "we will do it later" |
| External contributors do not appear | Medium | A production-ready repo that is *ready* for contributors is the goal; a missing contributor is not a blocker, but the readiness is |
| Scope creep into feature work | High | Every task in this document is remediation; new features are explicitly out of scope until all three docs' exit criteria are met |

---

## 9. Exit Criteria for This Document

All five DoD sections are complete **and**:

- The `:core-engine` dependency policy is a **written** ADR, enforced in CI, not an
  implicit convention.
- The system enters and exits degraded and safe-mode states under test, with metrics
  and page triggers.
- A tagged release `v0.1.0` is signed, published, and reproducible from its tag.
- Every operational state has a runbook, and at least one runbook has been validated
  by a drill with an engineer who did not write it.
- An external contributor (or a recorded dry-run) has landed a change through the full
  process — PR template, CODEOWNERS, CI, changelog, approval.

### The final review question

At the end of all three documents, the project should be able to answer **yes** to
every line of the original review's concerns:

| Original review finding | Resolved by |
|------------------------|-------------|
| Zero test coverage | Doc 01 Task 1, extended by Docs 02 and 03 |
| Hand-rolled `MiniJson` | Doc 01 Task 2 (port + adapter), Doc 03 Task 1 (ADR) |
| In-memory audit trail | Doc 01 Task 3 (persistent, hash-chained) |
| No CI | Doc 01 Task 4, extended by Docs 02 and 03 |
| Raw `HttpURLConnection` | Doc 02 Task 1 (port + OkHttp adapter + retry + SSE) |
| No observability | Doc 02 Task 2 (telemetry port + safe decorator) |
| Security shallow | Doc 02 Task 3 (threat model, fail-closed, schema validation) |
| No integration tests | Doc 02 Task 4 (E2E harness with golden audit files) |
| Zero-dependency rule unexamined | Doc 03 Task 1 (ADR with allowlist) |
| No scalability story | Doc 03 Task 2 (bounds, rate limiting, degradation) |
| No release process | Doc 03 Task 3 (signed, versioned, reproducible) |
| No operational docs | Doc 03 Task 4 (runbooks + monitoring spec) |
| No contribution process | Doc 03 Task 5 (governance, templates, CODEOWNERS) |

When the last exit criterion is met, the project can honestly claim — and prove — the
"production-grade" label the README currently asserts without evidence.