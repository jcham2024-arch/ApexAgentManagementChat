# PHASE-S3.2-TENANT-TESTS.md — Session Package, Part 6 of 7

**Version:** 1.0 · **Part 6 of 7:** §5.1–§5.6, existing test updates.
Parts: SESSION · CORE-PORT · SQLITE · POMS-RUNNER · CLI · TESTS ·
ISOLATION-CRITERIA, each `PHASE-S3.2-TENANT-<part>.md` (`AGENT_RULES.md`
§2.13).

---
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
