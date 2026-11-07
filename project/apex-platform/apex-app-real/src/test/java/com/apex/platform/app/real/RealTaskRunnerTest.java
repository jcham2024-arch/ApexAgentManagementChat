package com.apex.platform.app.real;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.cli.AutoApprove;
import com.apex.engine.cli.TaskDefinition;
import com.apex.engine.cli.TaskRunResult;
import com.apex.engine.cli.RealTaskRunner;
import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatEventSink;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.model.StepLogEntry;
import com.apex.engine.store.sqlite.SqliteStoragePort;
import com.apex.engine.tools.impl.KernelToolExecutor;
import com.apex.engine.planner.RiskLevel;
import com.apex.engine.tools.impl.RealPathJail;
import com.apex.platform.audit.InMemoryAuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealTaskRunnerTest {

    private static final String READ_ARCH =
            "{\"action\":\"read_file\",\"arguments\":{\"path\":\"ARCHITECTURE.md\"}}";
    private static final String WRITE_REPORT =
            "{\"action\":\"write_file\",\"arguments\":{\"path\":\"REPORT.md\","
            + "\"content\":\"Apex is a multi-agent platform.\"}}";
    private static final String DONE =
            "{\"done\":true,\"summary\":\"finished\"}";
    private static final TenantId TENANT = new TenantId("t-test");
    private static final AuditPort AUDIT = new InMemoryAuditLog(auditKey());
    private static final ApprovalPort APPROVE = new AutoApprove();
    private static final Map<String, RiskLevel> RISKS = Map.of(
            "read_file",  RiskLevel.L0,
            "write_file", RiskLevel.L1,
            "list_files", RiskLevel.L0);

    private static byte[] auditKey() {
        byte[] k = new byte[32];
        java.util.Arrays.fill(k, (byte) 0x42);
        return k;
    }

    @TempDir
    Path workspace;

    Path dbPath;
    SqliteStoragePort storage;
    ToolExecutorPort tools;

    /** Returns the scripted responses in order, repeating the last one. */
    private static final class ScriptedModel implements ChatModelPort {
        private final List<String> responses;
        private int next;

        ScriptedModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            String r = responses.get(Math.min(next++, responses.size() - 1));
            return new ChatResponse(r);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        dbPath = workspace.resolve(".apex/steps.db");
        Files.createDirectories(workspace.resolve(".apex"));
        storage = new SqliteStoragePort(dbPath);
        tools = new KernelToolExecutor(new RealPathJail(workspace), Map.of());
        Files.writeString(workspace.resolve("ARCHITECTURE.md"),
                "Apex is a multi-agent platform.");
    }

    @Test
    void twoStepTaskCompletes() {
        TaskRunResult result = run(List.of(READ_ARCH, DONE), task(List.of(), List.of(), 5));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.steps()).isEqualTo(2);
    }

    @Test
    void writeFileThenDoneSatisfiesAssertions() {
        TaskRunResult result = run(List.of(WRITE_REPORT, DONE),
                task(List.of("REPORT.md"),
                        List.of("file_contains:REPORT.md:Apex"), 5));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(workspace.resolve("REPORT.md")).exists();
    }

    @Test
    void doneOnFirstStepCompletesImmediately() {
        TaskRunResult result = run(List.of(DONE), task(List.of(), List.of(), 5));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.steps()).isEqualTo(1);
    }

    @Test
    void badJsonFromModelIsParseError() {
        TaskRunResult result = run(List.of("not json at all"),
                task(List.of(), List.of(), 5));

        assertThat(result.status()).isEqualTo("PARSE_ERROR");
    }

    @Test
    void repeatingActionHitsMaxSteps() {
        TaskRunResult result = run(List.of(READ_ARCH),
                task(List.of(), List.of(), 3));

        assertThat(result.status()).isEqualTo("MAX_STEPS");
        assertThat(result.steps()).isEqualTo(3);
    }

    @Test
    void failedAssertionIsReported() {
        TaskRunResult result = run(List.of(DONE),
                task(List.of(), List.of("file_exists:absent.md"), 5));

        assertThat(result.status()).isEqualTo("ASSERTION_FAILED");
    }

    @Test
    void toolErrorDoesNotStopTheLoop() {
        String missing = "{\"action\":\"read_file\","
                + "\"arguments\":{\"path\":\"absent.md\"}}";

        TaskRunResult result = run(List.of(missing, DONE),
                task(List.of(), List.of(), 5));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.steps()).isEqualTo(2);
    }

    @Test
    void everyStepIsRecordedToSqlite() throws Exception {
        run(List.of(READ_ARCH, DONE), task(List.of(), List.of(), 5));

        String sessionId = firstSessionId();
        List<StepLogEntry> entries = storage.stepsFor(TENANT, sessionId);

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(StepLogEntry::index)
                .containsExactly(0, 1);
        assertThat(entries).extracting(StepLogEntry::stepType)
                .containsExactly("action", "finish");
        assertThat(entries).extracting(e -> e.tenantId().value())
                .containsOnly("t-test");
    }

    private TaskRunResult run(List<String> responses, TaskDefinition task) {
        StoragePort recorder = storage;
        RealTaskRunner runner = new RealTaskRunner(
                new ScriptedModel(new ArrayList<>(responses)), tools, recorder,
                AUDIT, APPROVE, ChatEventSink.NOOP, RISKS, TENANT, workspace);
        return runner.run(task);
    }

    private static TaskDefinition task(List<String> outputFiles,
                                       List<String> assertions, int maxSteps) {
        return new TaskDefinition("t-1", "d", "i", maxSteps, "mock-model",
                List.of("read_file", "write_file"), List.of(), outputFiles,
                assertions);
    }

    private String firstSessionId() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT session_id FROM step_log ORDER BY step_index LIMIT 1")) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
