# PHASE-S3.3-AUDIT-RUNNER-TESTS.md — Session Package, Part 7 of 8

**Version:** 1.0 · **Part 7 of 8:** §7.4–§7.7, runner, audit, port and record tests.
Parts: SESSION · CORE-PORT · AUDIT-CORE · AUDIT-LOG · CLI · AUDIT-TESTS ·
RUNNER-TESTS · CRITERIA, each `PHASE-S3.3-AUDIT-<part>.md`
(`AGENT_RULES.md` §2.13).

---
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
