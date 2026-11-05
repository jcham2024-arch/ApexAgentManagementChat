# PHASE-S3.2-TENANT.md

**Version:** 1.0 · **Sub-phase of S3** · **Ends with cross-tenant
isolation proven by tests**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S3-SECURITY.md` (repo — router)
4. **This file**

## 1. Context notes — deltas for S3.2

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 `StepLogEntry` gains `tenantId` — this is a breaking change

S2.1 defined `StepLogEntry(stepId, sessionId, index, stepType,
durationMs)`. S3.2 adds a `TenantId tenantId` field. **Every
construction site across the tree must be updated.** Search for
`new StepLogEntry(` and fix each one. Do not add a secondary
constructor or a builder to preserve backwards compatibility — the
whole point of this phase is that every append is tenant-scoped.

### 1.2 `StoragePort.stepsFor` becomes tenant-scoped — breaking

S2.1 defined `stepsFor(String sessionId)`. S3.2 changes it to
`stepsFor(TenantId tenantId, String sessionId)`. The SQLite adapter
filters by tenant. Cross-tenant queries return empty even if the
session ID is guessed. Update every call site.

### 1.3 `TenantId` lives in `apex-chat-common`

`com.apex.chat.common.identity.TenantId` (from S1.1). `engine-core`,
`engine-store-sqlite`, and `engine-cli` all reference it. Add explicit
`apex-chat-common` dependencies to `engine-store-sqlite/pom.xml` and
`engine-cli/pom.xml` (currently available only transitively through
`engine-core`).

### 1.4 SQLite schema migration is `ALTER TABLE`, not drop-and-recreate

Existing databases from S2.5 do not have a `tenant_id` column. The
`initialize()` method must detect the missing column via `PRAGMA
table_info(step_log)` and run `ALTER TABLE step_log ADD COLUMN
tenant_id TEXT NOT NULL DEFAULT 'legacy-migration'` on old DBs. New
DBs get the column via the updated `CREATE TABLE`. Existing rows are
assigned the sentinel `legacy-migration`, which no real tenant will
ever use — those rows become unreachable via normal queries, which is
correct: they were written before tenant scoping existed and should be
treated as legacy.

### 1.5 `Main` accepts `--tenant <id>` and `APEX_TENANT_ID` env

Priority order: `--tenant` flag > `APEX_TENANT_ID` env var > default
`"cli-default"`. The default keeps S2.6 and S2.7 tests working without
change. Document the flag in `engine-cli/README.md`.

### 1.6 `apex-tenant` stays empty in S3.2

The module exists (S3.0 skeleton) but receives no code in S3.2. The
isolation proof lives in `engine-store-sqlite` tests, directly against
the port. Postgres RLS advisors — the router's original scope for
`apex-tenant` — are deferred to S6 when the Postgres adapter is added.
S3.2's `apex-tenant` module exists only so `CONTEXT.md` §4 is accurate;
do not add code to it.

### 1.7 `RealTaskRunner` gains a `TenantId` constructor parameter

The runner uses one tenant for the whole run. The `TenantId` goes into
every `StepLogEntry` it writes. It does not call `stepsFor` in
production code, so the query-side change does not affect it — but its
tests do.

### 1.8 `PortTest` and `PortRecordsTest` need updating

`engine-core`'s `PortTest.storagePortIsImplementable` constructs an
anonymous `StoragePort` — the `stepsFor` signature changes. Update it.
`PortRecordsTest` constructs `StepLogEntry` in several tests — add
`tenantId` to each.

### 1.9 Spec not required

Router §4 confirms S3.2 does not need `docs/apex-chat-spec/`. Do not
read spec parts. Do not create spec files.

## 2. Prerequisite

S3.1 merged to `main`. `mvn -B test` green. `JwtAuthFilter`, `Principal`,
`RbacPolicy`, `DefaultRbacPolicy` exist in `apex-auth`. If not, stop and
report.

## 3. Scope

Deliver:

1. `engine-core`: `StepLogEntry` gains `tenantId`;
   `StoragePort.stepsFor` becomes tenant-scoped
2. `engine-store-sqlite`: schema migration, tenant column, tenant
   filter in queries
3. `engine-cli`: `RealTaskRunner` gains `TenantId`;
   `Main` parses `--tenant` / `APEX_TENANT_ID`
4. Explicit `apex-chat-common` dependencies in `engine-store-sqlite`
   and `engine-cli`
5. Three existing test classes updated (`PortTest`,
   `PortRecordsTest`, `SqliteStoragePortTest`,
   `SqliteStoragePortConcurrencyTest`, `RealTaskRunnerTest`)
6. One new test class: `TenantIsolationTest`
7. `engine-cli/README.md` updated with the `--tenant` flag
8. **No** `docs/INVARIANTS.md` changes (E2 is proven in S3.5;
   tenant isolation is an S3 exit criterion, not a named invariant)

## 4. Production files to modify

### 4.1 `engine-core/src/main/java/com/apex/engine/core/port/model/StepLogEntry.java`

Replace the entire file with:

```java
package com.apex.engine.core.port.model;

import com.apex.chat.common.identity.TenantId;

import java.util.Objects;

/**
 * A single step record. {@code tenantId} is required and must match
 * the tenant of any store it is appended to. {@code sessionId} is a
 * raw string for now (assigned by the CLI as a UUID); typed session
 * IDs land in S6.
 */
public record StepLogEntry(
        String stepId,
        TenantId tenantId,
        String sessionId,
        int index,
        String stepType,
        long durationMs
) {
    public StepLogEntry {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(stepType, "stepType");
        if (index < 0) throw new IllegalArgumentException("index < 0");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs < 0");
        }
        if (stepId.isBlank()) {
            throw new IllegalArgumentException("stepId must not be blank");
        }
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (stepType.isBlank()) {
            throw new IllegalArgumentException("stepType must not be blank");
        }
    }
}
```

### 4.2 `engine-core/src/main/java/com/apex/engine/core/port/StoragePort.java`

Replace with:

```java
package com.apex.engine.core.port;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.model.StepLogEntry;

import java.util.List;

/**
 * Persistence for step and session logs. Every read is tenant-scoped:
 * {@link #stepsFor(TenantId, String)} returns only rows whose
 * {@code tenantId} matches the argument. Tier 1 impl:
 * engine-store-sqlite (S2.5). Tier 2 impl: adapter-spring-jdbc (S6+).
 */
public interface StoragePort {
    void appendStep(StepLogEntry entry);
    List<StepLogEntry> stepsFor(TenantId tenantId, String sessionId);
}
```

### 4.3 `engine-store-sqlite/src/main/java/com/apex/engine/store/sqlite/SqliteStoragePort.java`

Replace the entire file with:

```java
package com.apex.engine.store.sqlite;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.model.StepLogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SQLite implementation of {@link StoragePort}. Each operation opens
 * its own JDBC connection; WAL mode and a busy timeout are applied on
 * connect. Every query filters by {@code tenant_id} — a query for
 * tenant A never returns rows written by tenant B, regardless of the
 * session id passed in.
 */
public final class SqliteStoragePort implements StoragePort {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS step_log (
                step_id     TEXT PRIMARY KEY,
                tenant_id   TEXT NOT NULL,
                session_id  TEXT NOT NULL,
                step_index  INTEGER NOT NULL,
                step_type   TEXT NOT NULL,
                duration_ms INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_step_log_tenant_session
                ON step_log(tenant_id, session_id, step_index);
            """;

    private static final String MIGRATE_ADD_TENANT = """
            ALTER TABLE step_log
            ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'legacy-migration'
            """;

    private static final String INSERT_SQL = """
            INSERT INTO step_log
                (step_id, tenant_id, session_id, step_index, step_type,
                 duration_ms)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT step_id, tenant_id, session_id, step_index, step_type,
                   duration_ms
            FROM step_log
            WHERE tenant_id = ? AND session_id = ?
            ORDER BY step_index ASC
            """;

    private final Path dbPath;

    public SqliteStoragePort(Path dbPath) {
        Objects.requireNonNull(dbPath, "dbPath");
        this.dbPath = dbPath.toAbsolutePath();
        Path parent = this.dbPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new StorageException("init",
                        "cannot create database directory: " + parent, e);
            }
        }
        initialize();
    }

    private void initialize() {
        try (Connection conn = open();
             Statement st = conn.createStatement()) {
            for (String stmt : SCHEMA.split(";")) {
                if (!stmt.isBlank()) {
                    st.execute(stmt);
                }
            }
            if (!hasTenantColumn(conn)) {
                st.execute(MIGRATE_ADD_TENANT);
            }
        } catch (SQLException e) {
            throw new StorageException("init", e.getMessage(), e);
        }
    }

    private static boolean hasTenantColumn(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(step_log)")) {
            while (rs.next()) {
                if ("tenant_id".equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private Connection open() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
        }
        return conn;
    }

    @Override
    public void appendStep(StepLogEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, entry.stepId());
            ps.setString(2, entry.tenantId().value());
            ps.setString(3, entry.sessionId());
            ps.setInt(4, entry.index());
            ps.setString(5, entry.stepType());
            ps.setLong(6, entry.durationMs());
            ps.executeUpdate();
        } catch (SQLException e) {
            String category = isDuplicate(e) ? "duplicate" : "append";
            throw new StorageException(category, e.getMessage(), e);
        }
    }

    @Override
    public List<StepLogEntry> stepsFor(TenantId tenantId, String sessionId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, tenantId.value());
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<StepLogEntry> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new StepLogEntry(
                            rs.getString("step_id"),
                            new TenantId(rs.getString("tenant_id")),
                            rs.getString("session_id"),
                            rs.getInt("step_index"),
                            rs.getString("step_type"),
                            rs.getLong("duration_ms")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new StorageException("query", e.getMessage(), e);
        }
    }

    private static boolean isDuplicate(SQLException e) {
        return e.getErrorCode() == 19
                || e.getMessage().toLowerCase().contains("unique");
    }
}
```

### 4.4 `engine-store-sqlite/pom.xml` — add `apex-chat-common`

Insert inside `<dependencies>`, after the `engine-core` dependency:

```xml
<dependency>
    <groupId>com.apex</groupId>
    <artifactId>apex-chat-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

No other dependency change.

### 4.5 `engine-cli/src/main/java/com/apex/engine/cli/RealTaskRunner.java`

Replace the entire file with:

```java
package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.model.StepLogEntry;
import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Real task runner. ReAct loop over the model, tools, and storage.
 * Every {@link StepLogEntry} written carries the runner's tenant, so
 * cross-tenant queries cannot surface this run's steps.
 */
public final class RealTaskRunner implements TaskRunner {

    private final ChatModelPort model;
    private final ToolExecutorPort tools;
    private final StoragePort storage;
    private final TenantId tenantId;
    private final Path workspace;
    private final PromptBuilder prompts = new PromptBuilder();
    private final LlmResponseParser parser = new LlmResponseParser();
    private final AssertionChecker checker = new AssertionChecker();

    public RealTaskRunner(ChatModelPort model, ToolExecutorPort tools,
                          StoragePort storage, TenantId tenantId,
                          Path workspace) {
        this.model = Objects.requireNonNull(model, "model");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.storage = Objects.requireNonNull(storage, "storage");
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

### 4.6 `engine-cli/src/main/java/com/apex/engine/cli/Main.java`

Three changes to the existing file. Do not replace the whole file — make these edits.

**Edit 1:** Add to the imports block:
```java
import com.apex.chat.common.identity.TenantId;
```

**Edit 2:** In the `Args` record and its `parse` method, add a `tenant` field:
```java
record Args(boolean real, Path workspace, TenantId tenant, Path taskFile) {
    static Args parse(String[] args) {
        boolean real = false;
        Path workspace = Path.of(".").toAbsolutePath().normalize();
        String tenantStr = null;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--real")) { real = true; }
            else if (a.equals("--workspace")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--workspace needs a path");
                }
                workspace = Path.of(args[++i]).toAbsolutePath().normalize();
            } else if (a.equals("--tenant")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--tenant needs an id");
                }
                tenantStr = args[++i];
            } else if (a.startsWith("--")) {
                throw new IllegalArgumentException("unknown flag: " + a);
            } else {
                positional.add(a);
            }
        }
        if (positional.size() != 1) {
            throw new IllegalArgumentException(
                    "expected 1 task file, got " + positional.size());
        }
        TenantId tenant = tenantStr != null
                ? new TenantId(tenantStr)
                : null;
        return new Args(real, workspace, tenant,
                Path.of(positional.get(0)));
    }
}
```

**Edit 3:** In `run(...)`, after `Args parsed = Args.parse(args);`, resolve the tenant from `parsed.tenant()` or env, then pass it to `buildRealRunner`:

```java
TenantId tenant = parsed.tenant() != null
        ? parsed.tenant()
        : new TenantId(env.getOrDefault("APEX_TENANT_ID", "cli-default"));
```

And in `buildRealRunner`, add a `TenantId tenant` parameter and pass it to `RealTaskRunner`:

```java
return new RealTaskRunner(model, tools, storage, tenant, workspace);
```

The `buildRealRunner` signature becomes:
```java
private static TaskRunner buildRealRunner(TaskDefinition task,
                                          Map<String, String> env,
                                          Path workspace,
                                          TenantId tenant,
                                          PrintStream err) {
```

And the call site passes `tenant` as the fourth argument.

### 4.7 `engine-cli/pom.xml` — add `apex-chat-common`

Insert inside `<dependencies>`, after the `engine-core` dependency:

```xml
<dependency>
    <groupId>com.apex</groupId>
    <artifactId>apex-chat-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 4.8 `engine-cli/README.md`

Add a new section after "## Usage":

```markdown
## Tenant

Pass `--tenant <id>` or set `APEX_TENANT_ID`. Defaults to
`cli-default`. Every `StepLogEntry` written by the runner carries this
tenant, so cross-tenant queries cannot surface this run's steps.
```

## 5. Test files to modify

### 5.1 `engine-core/src/test/java/com/apex/engine/core/port/PortTest.java`

The `storagePortIsImplementable` test constructs an anonymous
`StoragePort`. Update the `stepsFor` method signature:

```java
@Override
public List<StepLogEntry> stepsFor(TenantId tenantId, String sessionId) {
    return List.of();
}
```

Add `import com.apex.chat.common.identity.TenantId;` to the imports.
Update the test body to call `port.stepsFor(new TenantId("t"), "s")`.
If `PortTest` also references other ports whose signatures did not
change, leave those alone.

### 5.2 `engine-core/src/test/java/com/apex/engine/core/port/model/PortRecordsTest.java`

Every construction of `new StepLogEntry(...)` must gain a
`new TenantId("t-test")` argument in the second position (after
`stepId`, before `sessionId`). Typical pattern:

```java
// Before
new StepLogEntry("id", "session", 0, "type", 0L)
// After
new StepLogEntry("id", new TenantId("t-test"), "session", 0, "type", 0L)
```

Add `import com.apex.chat.common.identity.TenantId;`.

Also add one new test proving the tenant component rejects null:

```java
@Test
void stepLogEntryRejectsNullTenant() {
    assertThatNullPointerException().isThrownBy(() ->
            new StepLogEntry("id", null, "session", 0, "type", 0L));
}
```

### 5.3 `engine-store-sqlite/src/test/java/com/apex/engine/store/sqlite/SqliteStoragePortTest.java`

Every `new StepLogEntry(...)` gains a `new TenantId("t-test")` second
argument. Every `port.stepsFor(sessionId)` becomes
`port.stepsFor(TENANT, sessionId)`. Define a constant at the top:

```java
private static final TenantId TENANT = new TenantId("t-test");
```

Add `import com.apex.chat.common.identity.TenantId;`.

Add a new test to this class — the schema-drift guard:

```java
@Test
void tenantIdColumnExists() throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
         Statement st = c.createStatement();
         ResultSet rs = st.executeQuery("PRAGMA table_info(step_log)")) {
        boolean found = false;
        while (rs.next()) {
            if ("tenant_id".equalsIgnoreCase(rs.getString("name"))) {
                found = true;
            }
        }
        assertThat(found).isTrue();
    }
}
```

The existing `createdAtNotInSchema` test must be updated: it asserts
the exact column set `{step_id, session_id, step_index, step_type,
duration_ms}`. Add `tenant_id` to that set.

### 5.4 `engine-store-sqlite/src/test/java/com/apex/engine/store/sqlite/SqliteStoragePortConcurrencyTest.java`

Same treatment: every `new StepLogEntry(...)` gains `new TenantId(...)`;
every `port.stepsFor(sessionId)` becomes `port.stepsFor(TENANT,
sessionId)`. Use one shared `TENANT` constant for the whole class —
concurrency semantics are not tenant-aware, so a single tenant is
correct.

### 5.5 `engine-cli/src/test/java/com/apex/engine/cli/RealTaskRunnerTest.java`

`new RealTaskRunner(model, tools, storage, workspace)` becomes
`new RealTaskRunner(model, tools, storage, TENANT, workspace)`. Define
`private static final TenantId TENANT = new TenantId("t-test");` at
the top of the class. Any direct `new StepLogEntry(...)` constructions
in this test gain the `TENANT` argument.

### 5.6 `engine-cli/src/test/java/com/apex/engine/cli/E2ETest.java`

No code change required if the test uses the default tenant — but
assert one thing: after the run, query the SQLite store directly and
verify the tenant is `cli-default`. This proves `Main` did the right
thing with the default path.

Optionally add `--tenant e2e` to the args array and `"e2e"` to the
assertion. Either way, add one line to the existing E2E test:

```java
try (Connection c = DriverManager.getConnection(
        "jdbc:sqlite:" + workspace.resolve(".apex/steps.db"))) {
    try (Statement st = c.createStatement();
         ResultSet rs = st.executeQuery(
             "SELECT DISTINCT tenant_id FROM step_log")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("cli-default");
    }
}
```

Add imports `java.sql.Connection`, `java.sql.DriverManager`,
`java.sql.ResultSet`, `java.sql.Statement`.

### 5.7 New file — `engine-store-sqlite/src/test/java/com/apex/engine/store/sqlite/TenantIsolationTest.java`

```java
package com.apex.engine.store.sqlite;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.model.StepLogEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIsolationTest {

    private static final TenantId TENANT_A = new TenantId("tenant-a");
    private static final TenantId TENANT_B = new TenantId("tenant-b");
    private static final String SESSION_A = "session-a";
    private static final String SESSION_B = "session-b";

    @TempDir Path tempDir;
    Path dbPath;
    SqliteStoragePort port;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("isolation.db");
        port = new SqliteStoragePort(dbPath);
    }

    private StepLogEntry entry(String id, TenantId tenant,
                               String session, int index) {
        return new StepLogEntry(id, tenant, session, index, "action", 0L);
    }

    @Test
    void tenantAQueryCannotSeeTenantBRows() {
        port.appendStep(entry("a1", TENANT_A, SESSION_A, 0));
        port.appendStep(entry("b1", TENANT_B, SESSION_B, 0));

        assertThat(port.stepsFor(TENANT_A, SESSION_A))
                .extracting(StepLogEntry::stepId)
                .containsExactly("a1");
    }

    @Test
    void tenantBCannotSeeTenantAWithGuessedSessionId() {
        port.appendStep(entry("a1", TENANT_A, SESSION_A, 0));

        assertThat(port.stepsFor(TENANT_B, SESSION_A)).isEmpty();
    }

    @Test
    void bothTenantsCoexistWithoutCrossTalk() {
        port.appendStep(entry("a1", TENANT_A, SESSION_A, 0));
        port.appendStep(entry("a2", TENANT_A, SESSION_A, 1));
        port.appendStep(entry("b1", TENANT_B, SESSION_B, 0));

        assertThat(port.stepsFor(TENANT_A, SESSION_A))
                .extracting(StepLogEntry::stepId)
                .containsExactly("a1", "a2");
        assertThat(port.stepsFor(TENANT_B, SESSION_B))
                .extracting(StepLogEntry::stepId)
                .containsExactly("b1");
    }

    @Test
    void rawSqlCannotCrossTenantsEvenWithoutPortGuards() throws Exception {
        port.appendStep(entry("a1", TENANT_A, SESSION_A, 0));
        port.appendStep(entry("b1", TENANT_B, SESSION_B, 0));

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM step_log " +
                     "WHERE tenant_id = 'tenant-a'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void legacyRowsDoNotLeakToAnyRealTenant() {
        // The migration sentinel is not a real tenant; no query for
        // TENANT_A or TENANT_B should ever return it.
        assertThat(port.stepsFor(TENANT_A, SESSION_A)).isEmpty();
        assertThat(port.stepsFor(TENANT_B, SESSION_B)).isEmpty();
    }
}
```

## 6. Exit criteria

- [ ] `mvn -B test` green. CI green.
- [ ] `StepLogEntry` has `tenantId` as a required field.
- [ ] `StoragePort.stepsFor` requires `TenantId`.
- [ ] SQLite schema has `tenant_id` column and
      `idx_step_log_tenant_session` index.
- [ ] Old DBs (from S2.5, no `tenant_id`) migrate cleanly via
      `ALTER TABLE`.
- [ ] `TenantIsolationTest` passes all 5 tests.
- [ ] `PortRecordsTest.stepLogEntryRejectsNullTenant` passes.
- [ ] `SqliteStoragePortTest.tenantIdColumnExists` passes.
- [ ] `SqliteStoragePortTest.createdAtNotInSchema` asserts the
      6-column set including `tenant_id`.
- [ ] `E2ETest` still passes and asserts tenant `cli-default` on the
      recorded steps.
- [ ] `RealTaskRunner` constructor takes `TenantId`; every test
      updated.
- [ ] `Main` accepts `--tenant` and `APEX_TENANT_ID`; default
      `cli-default`.
- [ ] `engine-store-sqlite/pom.xml` and `engine-cli/pom.xml` declare
      explicit `apex-chat-common` dependencies.
- [ ] `engine-cli/README.md` documents `--tenant`.
- [ ] `apex-tenant` module has **no** new code (only the S3.0
      `package-info`).
- [ ] No file exceeds 200 lines.
- [ ] No enterprise dependency added beyond `jjwt` (S3.1), which is
      unaffected.
- [ ] `docs/INVARIANTS.md` unchanged.

## 7. Do NOT add

- Any Spring, Jackson, Micrometer, Google, or Embabel dependency
- Any code in `apex-tenant` (Postgres RLS is S6+)
- Any RS256, JWT signing, or token issuance
- Any audit changes (S3.3)
- Any HITL changes (S3.4)
- Any retry logic
- Any change to `engine-planner`, `engine-tools-impl`,
      `engine-llm-http`, or `apex-auth`
- Any spec file
- Any file not listed in §4, §5

## 8. Commit

Three commits on `phase/s3.2-tenant`:

1. `feat(S3.2): add tenantId to StepLogEntry, scope stepsFor by tenant`
2. `feat(S3.2): migrate SQLite schema, filter queries by tenant`
3. `feat(S3.2): thread TenantId through RealTaskRunner and Main; add isolation tests`

Push. Report CI link, per-test-class pass counts, and confirm:
- `TenantIsolationTest.tenantBCannotSeeTenantAWithGuessedSessionId`
  passed
- `SqliteStoragePortTest.tenantIdColumnExists` passed
- `E2ETest` passed and asserted `cli-default` in `step_log`
- `PortRecordsTest.stepLogEntryRejectsNullTenant` passed

If CI fails twice on the same root cause, stop and report the log.

---

**End of S3.2 session package.**