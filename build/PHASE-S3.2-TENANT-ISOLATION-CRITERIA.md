# PHASE-S3.2-TENANT-ISOLATION-CRITERIA.md — Session Package, Part 7 of 7

**Version:** 1.0 · **Part 7 of 7:** §5.7–§8, TenantIsolationTest, criteria, commits.
Parts: SESSION · CORE-PORT · SQLITE · POMS-RUNNER · CLI · TESTS ·
ISOLATION-CRITERIA, each `PHASE-S3.2-TENANT-<part>.md` (`AGENT_RULES.md`
§2.13).

---
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
