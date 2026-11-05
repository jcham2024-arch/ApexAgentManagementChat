# PHASE-S3.3-AUDIT.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with a tamper-evident
audit chain and PII redaction proven by tests**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S3.3

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 `AuditPort` signature changes — breaking

S2.1 defined:
```java
void append(AuditEntry entry);
List<AuditEntry> all();
```

S3.3 changes to:
```java
AuditEntry append(String action, Instant at);
List<AuditEntry> all();
boolean verifyChain();
```

Rationale: the caller cannot compute the chain hash correctly without
access to the previous entry's hash. The port owns the chain. The old
`append(AuditEntry)` is removed.

### 1.2 `AuditEntry` gains `previousHash` — breaking

S2.1 defined `(String id, String action, Instant at, String hash)`.
S3.3 adds `String previousHash` (before `hash`). Genesis entries carry
`previousHash = ""`.

Every construction site across the tree must be updated. Search for
`new AuditEntry(`.

### 1.3 HMAC-SHA256 over `previousHash | id | action | atMillis`

The chain hash is computed by `HashChain.compute(key, previousHash, id,
action, atMillis)`. Concatenation uses `|` as delimiter. No external
library — `javax.crypto.Mac` is in `java.base`.

### 1.4 PII redaction happens inside the port, before hashing

`InMemoryAuditLog.append(action, at)` redacts `action` first, then
computes the hash over the redacted form. Two identical events with
different PII produce the same hash — which is correct, because the
stored action no longer distinguishes them.

### 1.5 In-memory only in S3.3

File-backed persistence of the audit log lands in S6 when the server
is wired. `InMemoryAuditLog` keeps the chain in a `List<AuditEntry>`.
`verifyChain()` re-walks it. Production secrets come from
`APEX_AUDIT_SECRET`; dev falls back to a hardcoded 32-byte key.

### 1.6 No spec needed

Router §4 confirms S3.3 does not need `docs/apex-chat-spec/`. Do not
read spec parts. Do not create spec files.

### 1.7 `RealTaskRunner` gains `AuditPort`, writes one entry per tool
call

Before each `tools.execute(call)`, the runner calls
`audit.append("tool_call:" + call.toolName(), Instant.now())`. Exactly
one entry per tool call. Args are **not** in the audit log — they may
contain secrets or PII that redaction might miss; the audit records
the fact of the call, not its content. Results are in `step_log`
(S2.5), not in audit.

### 1.8 `Main.buildRealRunner` wires `InMemoryAuditLog`

Uses `APEX_AUDIT_SECRET` env var if present; falls back to a hardcoded
32-byte dev key. `E2ETest` (S2.7) does not set the env var — it uses
the dev key and passes. Production deployments set
`APEX_AUDIT_SECRET`; the README documents this.

### 1.9 No `docs/INVARIANTS.md` change

E3 ("Sentinel failure → atomic rollback") is proven in S5. Audit chain
verification is an S3 exit criterion (router §5.D), not a named
invariant. `docs/INVARIANTS.md` stays unchanged in S3.3.

## 2. Prerequisite

S3.2 merged to `main`. `mvn -B test` green. `StepLogEntry` has
`tenantId`; `StoragePort.stepsFor` takes `TenantId`;
`RealTaskRunner` takes `TenantId`. If any is false, stop and report.

## 3. Scope

Deliver:

1. `apex-audit`: 4 source files, 3 test files
2. `engine-core`: `AuditPort` signature change (breaking),
   `AuditEntry` gains `previousHash` (breaking)
3. `engine-cli`: `RealTaskRunner` gains `AuditPort`; `Main` wires
   `InMemoryAuditLog`
4. Existing tests updated: `PortTest`, `PortRecordsTest`,
   `RealTaskRunnerTest`
5. New test: `RealTaskRunnerAuditTest`
6. `apex-audit/README.md` documents the chain format
7. **No** `docs/INVARIANTS.md` change

## 4. Files — `engine-core` (breaking changes)

### 4.1 `engine-core/src/main/java/com/apex/engine/core/port/model/AuditEntry.java`

Replace the entire file:

```java
package com.apex.engine.core.port.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A single entry in an append-only, hash-chained audit log. Each entry
 * commits to its predecessor's hash via {@link #previousHash}, so any
 * alteration to any entry breaks the chain from that point forward.
 *
 * <p>Genesis entries carry {@code previousHash = ""} (empty string, not
 * null). The {@code hash} field is an HMAC-SHA256 of
 * {@code previousHash | id | action | at.toEpochMilli()} keyed by a
 * secret the caller does not control.
 */
public record AuditEntry(
        String id,
        String action,
        Instant at,
        String previousHash,
        String hash
) {
    public AuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(previousHash, "previousHash");
        Objects.requireNonNull(hash, "hash");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (hash.isBlank()) {
            throw new IllegalArgumentException("hash must not be blank");
        }
    }
}
```

### 4.2 `engine-core/src/main/java/com/apex/engine/core/port/AuditPort.java`

Replace the entire file:

```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.AuditEntry;

import java.time.Instant;
import java.util.List;

/**
 * Append-only, tamper-evident audit log. The port owns the hash chain
 * and the PII redaction. Callers provide an action string and a
 * timestamp; the port returns the entry it created.
 *
 * <p>Tier 1 impl: InMemoryAuditLog (S3.3, in-memory) plus
 * file-backed (S6). Tier 2 impl: adapter-spring-audit (S6+).
 */
public interface AuditPort {

    /**
     * Redacts PII from {@code action}, computes the chain hash, appends
     * the entry, and returns it. The redacted form is what gets stored.
     */
    AuditEntry append(String action, Instant at);

    /** Returns an immutable snapshot of all entries, in append order. */
    List<AuditEntry> all();

    /**
     * Recomputes every hash in the chain and verifies that each entry's
     * {@code previousHash} matches its predecessor's {@code hash}.
     * Returns {@code false} if any check fails.
     */
    boolean verifyChain();
}
```

## 5. Files — `apex-audit`

Package `com.apex.platform.audit` for all files.

### 5.1 `AuditLogException.java`

```java
package com.apex.platform.audit;

/**
 * Failure from the audit log. Categories: {@code config},
 * {@code tamper}. Never thrown on a normal append — only on
 * construction (bad secret) or verification (broken chain).
 */
public final class AuditLogException extends RuntimeException {
    private final String category;

    public AuditLogException(String category, String message) {
        super(message);
        this.category = category;
    }

    public AuditLogException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
```

### 5.2 `HashChain.java`

```java
package com.apex.platform.audit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * HMAC-SHA256 hash of a single audit entry. The input is the
 * pipe-delimited concatenation of {@code previousHash | id | action |
 * at.toEpochMilli()}. Two entries with identical inputs produce
 * identical hashes. Any change to any field changes the hash.
 */
public final class HashChain {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public HashChain(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length < 32) {
            throw new AuditLogException("config",
                    "audit key must be at least 32 bytes");
        }
        this.key = key.clone();
    }

    public String compute(String previousHash, String id, String action,
                          long atMillis) {
        String input = previousHash + "|" + id + "|" + action + "|" + atMillis;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new AuditLogException("config",
                    "HMAC init failed: " + e.getMessage(), e);
        }
    }
}
```

### 5.3 `PiiRedactor.java`

```java
package com.apex.platform.audit;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based redaction of common PII categories. Replacement markers
 * are fixed strings (e.g. {@code [EMAIL]}) so identical inputs produce
 * identical outputs, preserving hash chain determinism.
 *
 * <p>Categories: email, phone, SSN, AWS access key, GitHub token,
 * OpenAI-style API key, PEM block. Applied in order; later patterns
 * see the output of earlier ones. Not a security boundary on its own
 * — the audit log is not the place where PII is sanitized from the
 * application; it is the place where PII that slipped through is
 * caught before being recorded.
 */
public final class PiiRedactor {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\d-])(\\+?\\d{1,3}[ .-]?)?\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]?\\d{4}(?![\\d-])");

    private static final Pattern SSN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private static final Pattern AWS_KEY = Pattern.compile(
            "\\bAKIA[0-9A-Z]{16}\\b");

    private static final Pattern GITHUB_TOKEN = Pattern.compile(
            "\\bghp_[A-Za-z0-9]{36}\\b");

    private static final Pattern OPENAI_KEY = Pattern.compile(
            "\\bsk-[A-Za-z0-9]{20,}\\b");

    private static final Pattern PEM = Pattern.compile(
            "-----BEGIN [A-Z ]+-----.*?-----END [A-Z ]+-----",
            Pattern.DOTALL);

    public String redact(String input) {
        Objects.requireNonNull(input, "input");
        String out = input;
        out = EMAIL.matcher(out).replaceAll("[EMAIL]");
        out = PHONE.matcher(out).replaceAll("[PHONE]");
        out = SSN.matcher(out).replaceAll("[SSN]");
        out = AWS_KEY.matcher(out).replaceAll("[AWS_KEY]");
        out = GITHUB_TOKEN.matcher(out).replaceAll("[GITHUB_TOKEN]");
        out = OPENAI_KEY.matcher(out).replaceAll("[API_KEY]");
        out = PEM.matcher(out).replaceAll("[PEM_BLOCK]");
        return out;
    }

    /** Returns true if {@code input} contains anything the redactor would replace. */
    public boolean containsPii(String input) {
        if (input == null) return false;
        return !input.equals(redact(input));
    }

    /** Unused but exported for future policy decisions. */
    static boolean matchesAnyPattern(String input) {
        if (input == null) return false;
        return EMAIL.matcher(input).find()
                || PHONE.matcher(input).find()
                || SSN.matcher(input).find()
                || AWS_KEY.matcher(input).find()
                || GITHUB_TOKEN.matcher(input).find()
                || OPENAI_KEY.matcher(input).find()
                || PEM.matcher(input).find();
    }
}
```

### 5.4 `InMemoryAuditLog.java`

```java
package com.apex.platform.audit;

import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.model.AuditEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory {@link AuditPort}. Append-only, hash-chained, PII-redacted.
 * Not persisted across process restarts; file-backed persistence lands
 * in S6. The chain is verified by walking every entry from genesis.
 *
 * <p>Thread-safe for appends via a single internal lock; the audit
 * log is not on the hot path, so contention is not a concern. Reads
 * return an immutable snapshot.
 */
public final class InMemoryAuditLog implements AuditPort {

    private final HashChain chain;
    private final PiiRedactor redactor;
    private final List<AuditEntry> entries = new ArrayList<>();

    public InMemoryAuditLog(byte[] key) {
        this(new HashChain(key), new PiiRedactor());
    }

    public InMemoryAuditLog(HashChain chain, PiiRedactor redactor) {
        this.chain = Objects.requireNonNull(chain, "chain");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
    }

    @Override
    public synchronized AuditEntry append(String action, Instant at) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(at, "at");

        String redactedAction = redactor.redact(action);
        String id = UUID.randomUUID().toString();
        String previousHash = entries.isEmpty()
                ? ""
                : entries.get(entries.size() - 1).hash();
        String hash = chain.compute(previousHash, id, redactedAction,
                at.toEpochMilli());

        AuditEntry entry = new AuditEntry(id, redactedAction, at,
                previousHash, hash);
        entries.add(entry);
        return entry;
    }

    @Override
    public synchronized List<AuditEntry> all() {
        return List.copyOf(entries);
    }

    @Override
    public synchronized boolean verifyChain() {
        String expectedPrevious = "";
        for (AuditEntry entry : entries) {
            if (!expectedPrevious.equals(entry.previousHash())) {
                return false;
            }
            String recomputed = chain.compute(entry.previousHash(),
                    entry.id(), entry.action(), entry.at().toEpochMilli());
            if (!recomputed.equals(entry.hash())) {
                return false;
            }
            expectedPrevious = entry.hash();
        }
        return true;
    }
}
```

### 5.5 Update `apex-audit` metadata

**`apex-platform/apex-audit/src/main/java/com/apex/platform/audit/package-info.java`** — replace:

```java
/**
 * Chained audit log with PII redaction. Tier 2. Implements AuditPort.
 * Hash chain uses HMAC-SHA256 over
 * previousHash|id|action|atMillis. In-memory only in S3.3;
 * file-backed persistence lands in S6.
 */
@NullMarked
package com.apex.platform.audit;

import org.jspecify.annotations.NullMarked;
```

**`apex-platform/apex-audit/README.md`** — replace:

```markdown
# apex-audit

**Tier:** 2 · **Purpose:** Chained audit log with PII redaction.

## Allowed dependencies
any (Tier 2); currently java.base + javax.crypto (from JRE) only

## Ports consumed
none

## Ports implemented
AuditPort (via InMemoryAuditLog)

## Chain format
Each entry commits to its predecessor via HMAC-SHA256 over
`previousHash|id|action|atMillis`. Genesis entries carry
`previousHash = ""`. Any alteration to any entry breaks the chain from
that point forward. Verification walks the chain from genesis and
recomputes every hash.

## PII redaction
Applied before hashing. Fixed-string replacement markers:
- `[EMAIL]`, `[PHONE]`, `[SSN]`, `[AWS_KEY]`, `[GITHUB_TOKEN]`,
  `[API_KEY]`, `[PEM_BLOCK]`

Identical redacted inputs produce identical hashes.

## Not implemented in S3.3
- File-backed persistence (S6)
- Monotonic timestamp enforcement (S6)
- Multi-writer coordination (S6)

## Tests
HashChainTest · PiiRedactorTest · InMemoryAuditLogTest
```

## 6. Files — `engine-cli`

### 6.1 `engine-cli/src/main/java/com/apex/engine/cli/RealTaskRunner.java`

Replace the entire file:

```java
package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.model.StepLogEntry;
import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Real task runner. ReAct loop over the model, tools, storage, and
 * audit. Every tool call is audited before execution (one entry per
 * call). Arguments are not recorded in the audit — they may contain
 * secrets the redactor might miss.
 */
public final class RealTaskRunner implements TaskRunner {

    private final ChatModelPort model;
    private final ToolExecutorPort tools;
    private final StoragePort storage;
    private final AuditPort audit;
    private final TenantId tenantId;
    private final Path workspace;
    private final PromptBuilder prompts = new PromptBuilder();
    private final LlmResponseParser parser = new LlmResponseParser();
    private final AssertionChecker checker = new AssertionChecker();

    public RealTaskRunner(ChatModelPort model, ToolExecutorPort tools,
                          StoragePort storage, AuditPort audit,
                          TenantId tenantId, Path workspace) {
        this.model = Objects.requireNonNull(model, "model");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    @Override
    public TaskRunResult run(TaskDefinition task) {
        String sessionId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        List<String> history = prompts.initialParts(task);

        for (int step = 0; step < task.maxSteps(); step++) {
            long stepStart = System.currentTimeMillis();
            ChatResponse resp;
            try {
                resp = model.chat(new ChatRequest(task.modelRoute(), history));
            } catch (RuntimeException e) {
                return new TaskRunResult(task.taskId(), "FAILED",
                        step, elapsed(start));
            }

            Decision decision;
            try {
                decision = parser.parse(resp.content());
            } catch (RuntimeException e) {
                return new TaskRunResult(task.taskId(), "PARSE_ERROR",
                        step, elapsed(start));
            }

            storage.appendStep(new StepLogEntry(
                    UUID.randomUUID().toString(),
                    tenantId,
                    sessionId, step,
                    decision instanceof Decision.Done ? "finish" : "action",
                    System.currentTimeMillis() - stepStart));

            if (decision instanceof Decision.Done) {
                List<AssertionChecker.CheckResult> checks =
                        checker.check(workspace, task);
                boolean ok = checker.allPassed(checks);
                return new TaskRunResult(task.taskId(),
                        ok ? "COMPLETED" : "ASSERTION_FAILED",
                        step + 1, elapsed(start));
            }

            Decision.Action action = (Decision.Action) decision;
            audit.append("tool_call:" + action.toolName(), Instant.now());

            ToolResult result = tools.execute(new ToolCall(
                    UUID.randomUUID().toString(),
                    action.toolName(), action.argumentsJson()));

            history = result.isError()
                    ? prompts.withToolResult(history, "ERROR: " + result.output())
                    : prompts.withToolResult(history, result.output());
        }
        return new TaskRunResult(task.taskId(), "MAX_STEPS",
                task.maxSteps(), elapsed(start));
    }

    private static long elapsed(long startMs) {
        return System.currentTimeMillis() - startMs;
    }
}
```

### 6.2 `engine-cli/src/main/java/com/apex/engine/cli/Main.java`

Three edits to the existing file.

**Edit 1:** Add imports:
```java
import com.apex.engine.core.port.AuditPort;
import com.apex.platform.audit.InMemoryAuditLog;
import java.nio.charset.StandardCharsets;
```

**Edit 2:** In `buildRealRunner`, after `StoragePort storage = ...`, add:

```java
byte[] auditKey = env.containsKey("APEX_AUDIT_SECRET")
        ? env.get("APEX_AUDIT_SECRET").getBytes(StandardCharsets.UTF_8)
        : DEV_AUDIT_KEY;
AuditPort audit = new InMemoryAuditLog(auditKey);
```

**Edit 3:** Change the `RealTaskRunner` construction from:

```java
return new RealTaskRunner(model, tools, storage, tenant, workspace);
```

to:

```java
return new RealTaskRunner(model, tools, storage, audit, tenant, workspace);
```

**Edit 4:** Add the `DEV_AUDIT_KEY` constant at the class level, with a
comment marking it as dev-only:

```java
/**
 * Development-only audit key. Production MUST set APEX_AUDIT_SECRET.
 * This 32-byte array is public and provides no tamper-evidence against
 * an attacker with read access to the source.
 */
private static final byte[] DEV_AUDIT_KEY = (
        "dev-only-do-not-use-in-production!!").getBytes(StandardCharsets.UTF_8);
```

The string literal is exactly 32 bytes. If it were shorter, `HashChain`
would reject it at construction.

### 6.3 `engine-cli/README.md`

Add a section after "## Tenant":

```markdown
## Audit

Every tool call is recorded in a hash-chained audit log. Production
deployments MUST set `APEX_AUDIT_SECRET` to a value at least 32 bytes
long. Without it, the CLI uses a hardcoded development key — the chain
verifies, but against a public secret, so tampering cannot be
detected. The audit log is in-memory only in S3.3; file persistence
lands in S6.
```

## 7. Test files

### 7.1 `apex-audit/src/test/java/com/apex/platform/audit/HashChainTest.java`

Five tests. Fixed key: `new byte[32]` filled with `0x42`.

1. `computesDeterministicHash` — same inputs twice → same hash
2. `differentInputsProduceDifferentHashes` — vary id
3. `previousHashAffectsResult` — same id/action/at, different previousHash → different hash
4. `actionChangeAltersHash`
5. `shortKeyRejected` — `new HashChain(new byte[16])` throws `AuditLogException("config")`

### 7.2 `apex-audit/src/test/java/com/apex/platform/audit/PiiRedactorTest.java`

Six tests:

1. `redactsEmail` — `"contact alice@example.com"` → `"contact [EMAIL]"`
2. `redactsSsn` — `"123-45-6789"` → `"[SSN]"`
3. `redactsAwsKey` — `"AKIAIOSFODNN7EXAMPLE"` → `"[AWS_KEY]"`
4. `redactsGithubToken` — `"ghp_" + 36 chars` → `"[GITHUB_TOKEN]"`
5. `leavesCleanTextUnchanged` — `"user logged in"` unchanged
6. `containsPiiDetects` — `containsPii("alice@x.com")` true; `containsPii("hello")` false

### 7.3 `apex-audit/src/test/java/com/apex/platform/audit/InMemoryAuditLogTest.java`

Ten tests. Fixed key: `new byte[32]` filled with `0x42`. Use a fixed
base instant `Instant.parse("2026-01-01T00:00:00Z")`.

1. `appendReturnsEntryWithComputedHash` — entry has non-blank hash; first entry has `previousHash == ""`
2. `secondEntryChainsToFirst` — `second.previousHash == first.hash`
3. `allReturnsImmutableSnapshot` — mutating the returned list throws
4. `allReturnsInAppendOrder`
5. `verifyChainOnEmptyLogReturnsTrue`
6. `verifyChainOnValidChainReturnsTrue` — three appends
7. `verifyChainOnTamperedActionReturnsFalse` — append one, then
   manually construct an `AuditEntry` with a different action and
   different hash but the same `previousHash`, feed it into a second
   `InMemoryAuditLog` that shares the key — cannot test tampering
   directly because fields are immutable. **Alternative test:**
   verify that recomputing the hash with different inputs produces a
   different result. This proves the tamper-detection mechanism, not
   the tamper itself. Use `HashChain` directly for this test.
8. `appendRedactsPii` — append `"login alice@example.com"`; query
   `all()`; assert `entry.action() == "login [EMAIL]"`
9. `identicalRedactedActionsProduceIdenticalHashes` — append
   `"a@b.com"` and `"c@d.com"` at the same instant; assert both
   entries have different hashes (different id) but the same redacted
   action
10. `verifyChainAfterManyAppends` — append 50 entries; assert
    `verifyChain()` returns true

Test #7 note: since `AuditEntry` is a record and `InMemoryAuditLog`
does not expose a mutation method, direct tampering is impossible
through the public API. The tamper-detection proof must construct a
`HashChain`, compute a hash for a specific entry, mutate the input,
and show the hash changes. That is a `HashChain` test, not an
`InMemoryAuditLog` test. Keep test #7 in `HashChainTest`, not here.
Replace test #7 with:

7. `chainCannotBeForgedWithoutSecret` — build a second
   `InMemoryAuditLog` with a different key; append the same action and
   instant to both; assert entries have different hashes

### 7.4 `engine-cli/src/test/java/com/apex/engine/cli/RealTaskRunnerTest.java`

`new RealTaskRunner(model, tools, storage, tenant, workspace)` becomes
`new RealTaskRunner(model, tools, storage, AUDIT, tenant, workspace)`.

Add at the top of the test class:
```java
private static final AuditPort AUDIT =
        new InMemoryAuditLog(new byte[]{/* 32 bytes */});
```

Fill the 32-byte array with a fixed value (e.g., `0x42` repeated).

Add import: `com.apex.engine.core.port.AuditPort;
com.apex.platform.audit.InMemoryAuditLog;`

No other change to the existing tests.

### 7.5 `engine-cli/src/test/java/com/apex/engine/cli/RealTaskRunnerAuditTest.java`

New file. Three tests. Uses the same scripted model pattern from
`RealTaskRunnerTest`.

```java
package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.model.AuditEntry;
import com.apex.engine.store.sqlite.SqliteStoragePort;
import com.apex.engine.tools.impl.KernelToolExecutor;
import com.apex.engine.tools.impl.RealPathJail;
import com.apex.platform.audit.InMemoryAuditLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealTaskRunnerAuditTest {

    private static final TenantId TENANT = new TenantId("t-audit");
    private static final byte[] AUDIT_KEY = new byte[32];

    static {
        java.util.Arrays.fill(AUDIT_KEY, (byte) 0x42);
    }

    @TempDir Path tempDir;
    RealPathJail jail;
    AuditPort audit;
    RealTaskRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        jail = new RealPathJail(tempDir);
        audit = new InMemoryAuditLog(AUDIT_KEY);
        KernelToolExecutor tools = new KernelToolExecutor(jail, Map.of());
        SqliteStoragePort storage = new SqliteStoragePort(
                tempDir.resolve("steps.db"));
        runner = new RealTaskRunner(new Scripted(List.of(
                "{\"action\":\"read_file\",\"arguments\":{\"path\":\"a.txt\"}}",
                "{\"done\":true,\"summary\":\"ok\"}"
        )), tools, storage, audit, TENANT, tempDir);
    }

    @Test
    void auditRecordsToolCall() {
        TaskDefinition task = task("t1");
        runner.run(task);

        List<AuditEntry> entries = audit.all();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).action()).isEqualTo("tool_call:read_file");
    }

    @Test
    void noToolCallsMeansNoAuditEntries() {
        KernelToolExecutor tools = new KernelToolExecutor(jail, Map.of());
        SqliteStoragePort storage = new SqliteStoragePort(
                tempDir.resolve("steps2.db"));
        RealTaskRunner direct = new RealTaskRunner(
                new Scripted(List.of("{\"done\":true,\"summary\":\"noop\"}")),
                tools, storage, audit, TENANT, tempDir);
        direct.run(task("t2"));

        assertThat(audit.all()).isEmpty();
    }

    @Test
    void chainVerifiesAfterRun() {
        runner.run(task("t3"));
        assertThat(audit.verifyChain()).isTrue();
    }

    private TaskDefinition task(String id) {
        return new TaskDefinition(id, "test", "do it", 5, "m",
                List.of("read_file"), List.of(), List.of(), List.of());
    }

    private record Scripted(List<String> responses) implements ChatModelPort {
        private static final java.util.concurrent.atomic.AtomicInteger NEXT
                = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public ChatResponse chat(ChatRequest req) {
            return new ChatResponse(responses.get(
                    Math.min(NEXT.getAndIncrement(), responses.size() - 1)));
        }
    }
}
```

### 7.6 `engine-core/src/test/java/com/apex/engine/core/port/PortTest.java`

The `auditPortIsImplementable` test constructs an anonymous `AuditPort`.
Update it to the new three-method signature:

```java
@Test
void auditPortIsImplementable() {
    AuditPort port = new AuditPort() {
        private final List<AuditEntry> entries = new java.util.ArrayList<>();
        @Override public AuditEntry append(String action, java.time.Instant at) {
            AuditEntry e = new AuditEntry(
                    java.util.UUID.randomUUID().toString(),
                    action, at, "", "hash");
            entries.add(e);
            return e;
        }
        @Override public List<AuditEntry> all() {
            return List.copyOf(entries);
        }
        @Override public boolean verifyChain() { return true; }
    };
    AuditEntry e = port.append("x", java.time.Instant.now());
    assertThat(e.action()).isEqualTo("x");
    assertThat(port.all()).hasSize(1);
    assertThat(port.verifyChain()).isTrue();
}
```

### 7.7 `engine-core/src/test/java/com/apex/engine/core/port/model/PortRecordsTest.java`

Every construction of `new AuditEntry(id, action, at, hash)` gains a
`""` (empty string) argument before `hash`:

```java
// Before
new AuditEntry("id", "action", Instant.now(), "abc")
// After
new AuditEntry("id", "action", Instant.now(), "", "abc")
```

Add one new test:

```java
@Test
void auditEntryRejectsNullPreviousHash() {
    assertThatNullPointerException().isThrownBy(() ->
            new AuditEntry("id", "action", Instant.now(), null, "hash"));
}
```

## 8. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `HashChainTest` passes all 5 tests (including the
      `chainCannotBeForgedWithoutSecret` proof in `InMemoryAuditLogTest`).
- [ ] `PiiRedactorTest` passes all 6 tests.
- [ ] `InMemoryAuditLogTest` passes all 10 tests, including
      `appendRedactsPii`.
- [ ] `RealTaskRunnerAuditTest` passes all 3 tests.
- [ ] `PortTest.auditPortIsImplementable` passes with the new three
      methods.
- [ ] `PortRecordsTest` updated; `auditEntryRejectsNullPreviousHash`
      passes.
- [ ] `RealTaskRunnerTest` updated for the new constructor; still
      passes.
- [ ] `E2ETest` still passes (it uses `Main.run`; `Main` uses
      `DEV_AUDIT_KEY`).
- [ ] `AuditPort` has `append(String, Instant)`, `all()`,
      `verifyChain()`.
- [ ] `AuditEntry` has `previousHash` before `hash`.
- [ ] `RealTaskRunner` writes one audit entry per tool call, before
      execution.
- [ ] `Main.buildRealRunner` wires `InMemoryAuditLog` using
      `APEX_AUDIT_SECRET` if present, `DEV_AUDIT_KEY` otherwise.
- [ ] `apex-audit/README.md` documents the chain format and dev key
      caveat.
- [ ] `engine-cli/README.md` documents `APEX_AUDIT_SECRET`.
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency added.
- [ ] `docs/INVARIANTS.md` unchanged.
- [ ] No spec files read or created.

## 9. Do NOT add

- Any Spring, Jackson, Micrometer, Google, or Embabel dependency
- File-backed persistence (S6)
- Monotonic timestamp enforcement (S6)
- Multi-writer coordination (S6)
- Audit on model calls — only tool calls in S3.3
- Any change to `engine-planner`, `engine-tools-impl`,
      `engine-llm-http`, `engine-store-sqlite`, or `apex-auth`
- Any HITL changes (S3.4)
- Any spec file
- Any file not listed in §4–§7

## 10. Commit

Four commits on `phase/s3.3-audit`:

1. `feat(S3.3): change AuditPort to own hash chain; add previousHash to AuditEntry`
2. `feat(S3.3): add HashChain, PiiRedactor, InMemoryAuditLog to apex-audit`
3. `feat(S3.3): wire AuditPort into RealTaskRunner and Main`
4. `test(S3.3): add hash chain, redaction, audit log, and runner audit tests`

Push. Report CI link, per-test-class pass counts, and confirm:
- `InMemoryAuditLogTest.appendRedactsPii` passed
- `InMemoryAuditLogTest.chainCannotBeForgedWithoutSecret` passed
- `RealTaskRunnerAuditTest.auditRecordsToolCall` passed
- `E2ETest` still passes
- `PortRecordsTest.auditEntryRejectsNullPreviousHash` passed

If CI fails twice on the same root cause, stop and report the log.

---

**End of S3.3 session package.**