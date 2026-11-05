# 02 — Short-Term Hardening: HTTP Resilience, Observability, Security, Integration Tests

> **Document type:** Instructional / Hardening Plan
> **Target repo:** `jcham2024-arch/Google-Ai-repo`
> **Scope:** Months 1–3 after `01-immediate-foundation.md` completes
> **Owner:** Project lead (you)
> **Prerequisite:** All exit criteria from `01-immediate-foundation.md` are met.
> Tests are green, JSON is behind a port, the audit trail persists, CI guards C1–C5.

---

## 0. Purpose

Document 01 made the project **trustworthy** (tests, CI, durable audit, safe JSON).
This document makes it **operable and resilient** against the three realities of
production LLM workloads:

1. **The network is hostile** — rate limits, timeouts, partial failures, streaming.
2. **You cannot debug what you cannot see** — no logs, metrics, or traces today.
3. **The attack surface is real** — model output drives tool execution.

Four tasks close these gaps. Work them **in the order given** — Task 3 (security)
depends on Task 2 (observability) being able to *see* rejected/blocked events.

---

## 1. Constraints Carried Forward (unchanged from Document 01)

| # | Constraint | Enforcement |
|---|-----------|-------------|
| C1 | Dependency direction stays `:app` → `:security-interface` → `:core-engine` | CI (Doc 01) |
| C2 | `:core-engine` has **no third-party runtime dependencies** and **no Android imports** | CI (Doc 01) |
| C3 | Ports live in `:core-engine`; implementations live in `:security-interface` or `:app` | Review |
| C4 | Every new public type gets at least one unit test | Coverage gate (Doc 01) |
| C5 | No secrets, keys, or tokens in source | gitleaks (Doc 01) |

**New constraints added by this document:**

| # | Constraint | Enforcement |
|---|-----------|-------------|
| C6 | Every network call goes through a `HttpTransportPort` — no raw `HttpURLConnection` in feature code | CI grep (Task 1) |
| C7 | Every tool invocation emits exactly one structured log line at `INFO` and one span | Integration test (Task 4) |
| C8 | No `println` / `printStackTrace` / `System.out` in any module | CI grep (Task 2) |
| C9 | A failing `TelemetryPort` implementation must never crash the engine | Fault-injection test (Task 2) |

---

## 2. Task 1 — Harden the HTTP Client Behind a Port

### 2.1 Goal
Replace the raw `HttpURLConnection` usage in `OpenAiCompatibleClient` with a
port-driven transport that supports timeouts, retries with exponential backoff,
connection pooling, and **Server-Sent Events (SSE) streaming** — the default mode for
OpenAI-compatible chat APIs.

### 2.2 Step 1 — Define the transport port (in `:core-engine`)

```kotlin
// core-engine/src/main/kotlin/.../port/HttpTransportPort.kt
package ...port

interface HttpTransportPort {
    /** One-shot request/response. */
    suspend fun execute(request: HttpRequest): HttpResponse

    /** Streaming request; emits chunks as they arrive. Cancellable. */
    fun stream(request: HttpRequest): Flow<HttpChunk>
}

data class HttpRequest(
    val method: String,                 // "GET", "POST", ...
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
    val timeoutMs: Long = 30_000,
)

sealed interface HttpResponse {
    data class Success(
        val status: Int,
        val headers: Map<String, String>,
        val body: String,
    ) : HttpResponse
    data class Failure(
        val status: Int?,                   // null = transport-level failure
        val retryable: Boolean,
        val cause: String,
    ) : HttpResponse
}

sealed interface HttpChunk {
    data class Data(val text: String) : HttpChunk   // one SSE "data:" payload
    data object Done : HttpChunk
    data class Error(val retryable: Boolean, val cause: String) : HttpChunk
}
```

> **Design note:** `Failure.retryable` is a **decision**, not a fact. The adapter
> (Task 1.4) makes it, because only the adapter knows the difference between a
> `429` (retry) and a `401` (never retry). The engine only reacts.

### 2.3 Step 2 — Define the retry policy (in `:core-engine`)

```kotlin
// core-engine/src/main/kotlin/.../port/RetryPolicy.kt
data class RetryPolicy(
    val maxAttempts: Int = 4,
    val baseDelayMs: Long = 500,
    val maxDelayMs: Long = 8_000,
    val jitterFraction: Double = 0.2,
) {
    fun delayForAttempt(attempt: Int): Long {
        val exp = baseDelayMs * (1L shl (attempt - 1)).coerceAtMost(maxDelayMs)
        val jitter = (exp * jitterFraction * (Math.random() * 2 - 1)).toLong()
        return (exp + jitter).coerceIn(0, maxDelayMs)
    }
}
```

Unit-test `delayForAttempt` for monotonic growth, cap, and jitter bounds (this is
cheap and catches off-by-one errors).

### 2.4 Step 3 — Implement the adapter (in `:app`)

Use **OkHttp** — it gives connection pooling, timeouts, and an SSE-friendly
`ResponseBody.source()` for free. This is an allowed dependency because `:app` has no
C2 restriction.

```kotlin
// app/src/main/kotlin/.../adapter/OkHttpTransport.kt
class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build(),
    private val policy: RetryPolicy = RetryPolicy(),
) : HttpTransportPort {

    override suspend fun execute(request: HttpRequest): HttpResponse =
        withContext(Dispatchers.IO) { executeWithRetry(request) }

    override fun stream(request: HttpRequest): Flow<HttpChunk> = flow {
        // Build a Request with header Accept: text/event-stream, then:
        //   client.newCall(req).execute().use { resp ->
        //     resp.body!!.source().use { src ->
        //       while (!src.exhausted()) emit(parseSseLine(src.readUtf8Line()!!))
        //     }
        //   }
        // Emit HttpChunk.Data / Done / Error, honoring cancellation.
    }.flowOn(Dispatchers.IO)

    private suspend fun executeWithRetry(request: HttpRequest): HttpResponse { /* ... */ }
}
```

**Retry rules — pin these in code, not in prose:**

| Status / Condition | Retry? | Notes |
|--------------------|--------|-------|
| `429 Too Many Requests` | Yes | Honor `Retry-After` header if present |
| `500`, `502`, `503`, `504` | Yes | Server-side transient |
| `408 Request Timeout` | Yes | |
| `400`, `401`, `403`, `404`, `422` | **No** | Client error — retrying will not help |
| `IOException` / `SocketTimeoutException` | Yes | Transport-level |
| Cancellation (`CancellationException`) | **No** | Propagate immediately |
| Streaming after first byte received | **No** | Never replay a partially-consumed stream |

### 2.5 Step 4 — Migrate `OpenAiCompatibleClient`

1. `OpenAiCompatibleClient` takes `HttpTransportPort` (not `HttpURLConnection`) via constructor.
2. Delete the raw `HttpURLConnection` block.
3. Add `:core-engine` `ChatModelPort` a **`streamChat(...): Flow<ChatDelta>`** method.
   This is a port change — bump the port interface version and update every adapter.
4. Parse SSE chunks with the `JsonCodecPort` from Doc 01. No string splitting.

### 2.6 Step 5 — Tests

- **Unit:** `RetryPolicyTest` (delay math).
- **Unit:** `OkHttpTransportTest` using **MockWebServer** — verify retry counts,
  honor `Retry-After`, no retry on 4xx, cancellation propagates.
- **Unit:** `SseParserTest` — split frames across buffer boundaries, multi-line `data:`,
  `[DONE]` sentinel, malformed frame → `HttpChunk.Error`.
- **Integration:** covered in Task 4.

### 2.7 Task 1 — Definition of Done

- [ ] `HttpTransportPort`, `HttpRequest`, `HttpResponse`, `HttpChunk`, `RetryPolicy` in `:core-engine`
- [ ] `OkHttpTransport` in `:app`, wired into `OpenAiCompatibleClient`
- [ ] `ChatModelPort.streamChat` returns `Flow<ChatDelta>`; adapter implements it
- [ ] MockWebServer tests prove retry semantics for all rows in the table above
- [ ] Cancellation test: cancelling the collector stops the OkHttp call
- [ ] CI guard: `grep -rn "HttpURLConnection" app/src/main` returns nothing
- [ ] `docs/networking.md` documents timeouts, retry policy, and SSE assumptions

---

## 3. Task 2 — Implement Observability

### 3.1 Goal
Make every tool invocation and model call **visible**: one structured log line, one
trace span, and a handful of metrics. And make telemetry **unbreakable** — a broken
exporter must not take down the engine.

### 3.2 Step 1 — Define the telemetry port (in `:core-engine`)

```kotlin
// core-engine/src/main/kotlin/.../port/TelemetryPort.kt
package ...port

interface TelemetryPort {
    fun startSpan(name: String, attributes: Map<String, String> = emptyMap()): Span
    fun counter(name: String, delta: Long = 1, tags: Map<String, String> = emptyMap())
    fun histogram(name: String, valueMs: Long, tags: Map<String, String> = emptyMap())
    fun log(level: Level, event: String, fields: Map<String, Any?> = emptyMap())

    interface Span : AutoCloseable {
        fun setAttribute(key: String, value: String)
        fun recordException(t: Throwable)
        override fun close()          // span ends, duration recorded
    }
    enum class Level { DEBUG, INFO, WARN, ERROR }
}
```

Provide a **`NoOpTelemetry`** implementation in `:core-engine` so the module remains
standalone and Constraint C2 holds.

### 3.3 Step 2 — Implement the adapter (in `:app`)

Use **OpenTelemetry SDK** + **SLF4J + Logback** for structured logs (JSON encoder).

```kotlin
// app/src/main/kotlin/.../adapter/OtelTelemetry.kt
class OtelTelemetry(
    private val tracer: Tracer,
    private val meter: Meter,
    private val log: Logger,
) : TelemetryPort {

    override fun startSpan(name: String, attributes: Map<String, String>): TelemetryPort.Span {
        val span = tracer.spanBuilder(name).startSpan()
        attributes.forEach { span.setAttribute(it.key, it.value) }
        return OtelSpan(span)
    }

    override fun log(level: TelemetryPort.Level, event: String, fields: Map<String, Any?>) {
        log.atLevel(level.toSlf4j()).addKeyValue("event", event)
            .also { b -> fields.forEach { (k, v) -> b.addKeyValue(k, v) } }
            .log()
    }
    // ...
}
```

### 3.4 Step 3 — Fault isolation (Constraint C9)

Wrap every public method of `TelemetryPort` with a **`SafeTelemetryDecorator`** that
catches `Throwable` from the delegate and drops it on the floor (log to stderr at most,
once per minute to avoid log storms):

```kotlin
class SafeTelemetryDecorator(private val delegate: TelemetryPort) : TelemetryPort {
    override fun startSpan(name: String, attributes: Map<String, String>): TelemetryPort.Span =
        try { delegate.startSpan(name, attributes) }
        catch (t: Throwable) { NoOpTelemetry.NoOpSpan }

    override fun counter(name: String, delta: Long, tags: Map<String, String>) =
        try { delegate.counter(name, delta, tags) } catch (_: Throwable) {}

    // ...same for every method
}
```

**The engine only ever sees the decorator.** The decorator is tested with a
deliberately-throwing fake — that is the fault-injection test referenced in C9.

### 3.5 Step 4 — Instrument the critical paths (Constraint C7)

Every tool invocation must produce, without the feature author having to remember:

| Signal | Name | Attributes |
|--------|------|-----------|
| Span | `tool.invoke` | `tool.id`, `risk.level`, `outcome` |
| Log | `tool.invoked` | `tool.id`, `risk.level`, `duration.ms`, `approved` |
| Counter | `tool.invocations` | `tool.id`, `risk.level` |
| Histogram | `tool.duration_ms` | `tool.id` |
| Counter | `security.denials` | `tool.id`, `reason` |

Enforce this in **one place**: a `ToolInvocationInterceptor` in `:security-interface`
that wraps `ToolExecutorPort.execute`. Do **not** sprinkle telemetry calls across
features — that is how coverage decays.

```kotlin
class ToolInvocationInterceptor(
    private val delegate: ToolExecutorPort,
    private val telemetry: TelemetryPort,
) : ToolExecutorPort {
    override suspend fun execute(call: ToolCall): ToolResult {
        val attrs = mapOf("tool.id" to call.toolId, "risk.level" to call.risk.name)
        return telemetry.startSpan("tool.invoke", attrs).use { span ->
            val start = System.currentTimeMillis()
            val result = try { delegate.execute(call) }
                catch (t: Throwable) { span.recordException(t); throw t }
            val ms = System.currentTimeMillis() - start
            telemetry.log(INFO, "tool.invoked", attrs + mapOf(
                "duration.ms" to ms,
                "outcome" to result.outcome.name,
            ))
            telemetry.histogram("tool.duration_ms", ms, attrs)
            span.setAttribute("outcome", result.outcome.name)
            result
        }
    }
}
```

### 3.6 Step 5 — Remove `println` / `System.out` (Constraint C8)

1. `grep -rn "System\.out\|println\|printStackTrace" --include=*.kt` — audit every hit.
2. Replace each with `TelemetryPort.log(...)`.
3. Add the CI guard.

### 3.7 Task 2 — Definition of Done

- [ ] `TelemetryPort`, `NoOpTelemetry` in `:core-engine`; KDoc describes semantics
- [ ] `OtelTelemetry`, `SafeTelemetryDecorator` in `:app`
- [ ] Fault-injection test: a throwing delegate does not crash a tool invocation
- [ ] `ToolInvocationInterceptor` emits span + log + counter + histogram per call
- [ ] Zero `println` / `printStackTrace` / `System.out` in source
- [ ] CI guard for C8 added
- [ ] `docs/observability.md` documents every signal name and its attributes

---

## 4. Task 3 — Security Review & Hardening

### 4.1 Goal
Turn the thoughtful-on-paper security model into one that is **enforced**, **fail-closed**,
and **reviewed against a written threat model**.

### 4.2 Step 1 — Write the threat model

Create `docs/security/threat-model.md`. It must contain **at minimum**:

1. **Trust boundaries** — diagram showing user input, model output, tool execution,
   audit store, and secrets. Everything on the model-output side of the boundary is
   untrusted.
2. **Assets** — secrets, audit trail integrity, user files, network egress.
3. **Threats** — enumerate for each trust boundary. Minimum set:
   - T1 Prompt injection → tool escalation
   - T2 Malicious model output → arbitrary argument injection into tools
   - T3 Audit trail tampering or truncation
   - T4 Secret exfiltration via tool output
   - T5 Denial of service via unbounded tool loops
4. **Mitigations** — map each threat to a **named control** and a **test**.
5. **Residual risks** — what you accept, and why.

This document is the **source of truth** for everything below. If a mitigation is
added in code and not listed here, the PR is incomplete.

### 4.3 Step 2 — Make `SecurityInterlock` fail-closed

Required behavior, each with a unit test:

| Scenario | Behavior |
|----------|----------|
| Unknown tool id | **Deny** |
| Unknown risk level string | **Deny** |
| `ApprovalPort` throws | **Deny** (do not catch-and-allow) |
| `ApprovalPort` returns null | **Deny** |
| Tool at L3+ invoked without explicit approval record | **Deny** |
| Approval record older than `maxApprovalAgeMs` (default 5 min) | **Deny** |
| Approval record for a different request id | **Deny** |

Property test idea: generate random combinations of the above and assert **`Deny` is
the outcome for every malformed input**. This is cheap and catches regressions.

### 4.4 Step 3 — Validate tool arguments

Every `ToolExecutorPort` adapter must declare a **schema** (from `JsonCodecPort`'s
world) and the interceptor must validate before execution:

```kotlin
interface ToolExecutorPort {
    val schema: ToolSchema
    suspend fun execute(call: ToolCall): ToolResult
}
```

Reject on any schema violation. **Never** pass raw model output to a shell, file path,
URL, or SQL query. Document this in `docs/security/tool-adapters.md` with a checklist
every new adapter must satisfy.

### 4.5 Step 4 — Bound tool loops

Add a `maxToolIterations` (default 10) and a per-tool budget to the engine loop. Exceed
either → abort with `BudgetExhaustedException`. This closes T5.

### 4.6 Step 5 — Secret hygiene

- Confirm `gitleaks` (Doc 01) is scanning history, not just the diff.
- Add a pre-commit hook (`.git/hooks/pre-commit` or `lefthook`) that runs `gitleaks protect --staged`.
- Ensure secrets are read from the keystore at runtime only, never logged. Add a test
  that `TelemetryPort.log` **redacts** any field matching `(?i)(token|secret|key|password)`.

### 4.7 Task 3 — Definition of Done

- [ ] `docs/security/threat-model.md` merged and reviewed
- [ ] Every fail-closed row above has a passing unit test
- [ ] Property test proves malformed inputs always deny
- [ ] Tool adapters declare schemas; interceptor validates before execution
- [ ] `maxToolIterations` and per-tool budget enforced and tested
- [ ] Telemetry redacts secret-shaped fields (test present)
- [ ] `docs/security/tool-adapters.md` checklist published

---

## 5. Task 4 — Integration Tests (End-to-End)

### 5.1 Goal
Prove the whole pipeline works with a **real-ish model server** and **real-ish tools**:
`:app` → `:security-interface` → `:core-engine`.

### 5.2 Step 1 — Build a mock LLM server

Two options; pick one:

| Option | Pros | Cons |
|--------|------|------|
| **MockWebServer** (already a dep from Task 1) | No new infra | Scripted responses only |
| **WireMock** | Richer matchers, stateful | Larger footprint |

**Recommendation:** MockWebServer for now. Encode responses as fixtures under
`app/src/test/resources/llm-fixtures/`:

```
llm-fixtures/
  simple-reply.json
  tool-call-single.json
  tool-call-nested-args.json
  streamed-reply.sse
  rate-limited-then-ok.json
```

Each fixture is a real OpenAI-compatible payload captured from a live call once, then
frozen.

### 5.3 Step 2 — Write the five required end-to-end tests

File: `app/src/test/kotlin/.../e2e/AgentPipelineE2ETest.kt`

```kotlin
@Test fun `simple reply is returned and audited`() { /* ... */ }

@Test fun `tool call flows through interlock and executes with audit + telemetry`() { /* ... */ }

@Test fun `L3 tool without approval is denied and audit records the denial`() { /* ... */ }

@Test fun `rate limited request retries and succeeds`() { /* ... */ }

@Test fun `streamed reply is delivered incrementally`() { /* ... */ }
```

Assertions on **every** test:

1. `AuditStorePort.verify()` returns `null` (chain intact).
2. The audit trail contains the exact `tool.id` and outcome.
3. A captured `TelemetryPort` fake received the expected span + counter + log.
4. The response body round-trips through `JsonCodecPort` unchanged for the nested-args fixture.

### 5.4 Step 3 — Wire into CI as a separate job

Add `e2e` job to `ci.yml` (from Doc 01):

```yaml
  e2e:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew :app:e2eTest
      - uses: actions/upload-artifact@v4
        if: failure()
        with: { name: e2e-reports, path: '**/build/reports/tests/**' }
```

### 5.5 Step 4 — Golden files for the audit trail

Store the expected `audit-*.jsonl` for each test as a golden file. Diff on every run.
A change to the hash formula or record schema **must** update the goldens and the ADR
in Doc 01 — this is the guard that prevents silent audit-format drift.

### 5.6 Task 4 — Definition of Done

- [ ] Mock LLM server or MockWebServer harness wired
- [ ] Fixtures frozen under `app/src/test/resources/llm-fixtures/`
- [ ] All five E2E tests pass
- [ ] Golden audit files checked on every run
- [ ] `e2e` job in CI, green on `main`
- [ ] `docs/e2e.md` describes how to add a fixture and a scenario

---

## 6. Sequencing & Effort Estimate

| Order | Task | Est. effort | Depends on |
|-------|------|-------------|-----------|
| 1 | HTTP transport + retry + SSE | 4–5 days | Doc 01 complete |
| 2 | Observability + interceptor | 3–4 days | Task 1 (streaming shapes) |
| 3 | Security hardening | 3–4 days | Task 2 (sees denials) |
| 4 | E2E harness + tests | 3–4 days | Tasks 1–3 |

Total: **~13–17 working days** for one focused engineer.

> Tasks 1 and 2 share the `ChatDelta` port change — land them together or the port
> will thrash. Task 3 depends on Task 2 to observe denials. Task 4 is the proof; it
> **must** come last.

---

## 7. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| `ChatModelPort.streamChat` breaks existing adapters | High | Introduce as a **defaulted** method returning `flowOf(single-shot)` first, migrate adapters, then remove default |
| Retry logic masks real failures in tests | Medium | MockWebServer asserts **call count** explicitly; never assert only "eventually succeeds" |
| Telemetry SDK adds noticeable latency | Low | Measure with the histogram from Task 2; batch exports; use the decorator (C9) so exporter hiccups don't block |
| Threat model becomes a stale document | Medium | Every security-relevant PR must touch it; add to the PR checklist |
| E2E fixtures drift from real OpenAI payloads | Medium | Re-capture fixtures quarterly; pin the capture date in each file header |
| MockWebServer cannot simulate streaming nuances | Low | Write a manual smoke test against a real endpoint nightly, not in CI |

---

## 8. Exit Criteria for This Document

All four DoD sections are complete **and**:

- A `429` response from the mock server triggers exactly the expected retries and then succeeds.
- A streamed reply delivers chunks incrementally (asserted by timestamping in the test).
- Killing the telemetry exporter mid-run does not affect any tool invocation.
- The property test for `SecurityInterlock` proves malformed input always denies.
- The end-to-end pipeline produces a valid, golden-matching audit trail for all five scenarios.
- No `println`, `printStackTrace`, or `System.out` remain in any module.

Proceed to **`03-long-term-production-readiness.md`** once these exit criteria are met.