package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.model.AuditEntry;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.planner.RiskLevel;
import com.apex.engine.store.sqlite.SqliteStoragePort;
import com.apex.engine.tools.impl.KernelToolExecutor;
import com.apex.engine.tools.impl.RealPathJail;
import com.apex.platform.audit.InMemoryAuditLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealTaskRunnerApprovalTest {

    private static final TenantId TENANT = new TenantId("t-hitl");
    private static final byte[] AUDIT_KEY = key();
    private static final Map<String, RiskLevel> RISKS = Map.of(
            "read_file",   RiskLevel.L0,
            "run_command", RiskLevel.L4);

    private static byte[] key() {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) 0x42);
        return k;
    }

    @TempDir
    Path tempDir;

    RealPathJail jail;
    InMemoryAuditLog audit;
    Path echoBinary;

    @BeforeEach
    void setUp() throws Exception {
        echoBinary = findEcho();
        jail = new RealPathJail(tempDir);
        audit = new InMemoryAuditLog(AUDIT_KEY);
        Files.writeString(tempDir.resolve("a.txt"), "hi");
    }

    @Test
    void l0ToolDoesNotRequireApproval() {
        RealTaskRunner runner = buildRunner(List.of(readFile(), done()), new AutoDeny());

        TaskRunResult result = runner.run(task("t1", "read_file"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.steps()).isEqualTo(2);
    }

    @Test
    void l4ToolBlocksWhenApprovalDenied() {
        RealTaskRunner runner = buildRunner(
                List.of(runEcho(), done()), new AutoDeny());

        TaskRunResult result = runner.run(task("t2", "run_command"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(audit.all()).extracting(AuditEntry::action)
                .contains("tool_call:run_command");
    }

    @Test
    void l4ToolExecutesWhenApprovalGranted() {
        RealTaskRunner runner = buildRunner(
                List.of(runEcho(), done()), new AutoApprove());

        TaskRunResult result = runner.run(task("t3", "run_command"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(audit.all()).extracting(AuditEntry::action)
                .contains("tool_call:run_command");
    }

    @Test
    void unknownToolDefaultsToL3AndRequiresApproval() {
        RealTaskRunner runner = buildRunner(
                List.of("{\"action\":\"mystery\",\"arguments\":{}}", done()),
                new AutoDeny());

        TaskRunResult result = runner.run(task("t4", "mystery"));

        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    void repeatedDenialsTerminateAtMaxSteps() {
        RealTaskRunner runner = buildRunner(List.of(runEcho()), new AutoDeny());

        TaskRunResult result = runner.run(task("t5", "run_command", 3));

        assertThat(result.status()).isEqualTo("MAX_STEPS");
        assertThat(result.steps()).isEqualTo(3);
    }

    @Test
    void denialIsFedBackToTheModelAsToolError() {
        ScriptedModel model = ScriptedModel.recording(List.of(runEcho(), done()));
        RealTaskRunner runner = buildRunner(model, new AutoDeny());

        runner.run(task("t6", "run_command"));

        assertThat(model.lastPrompt()).contains("approval denied");
    }

    private static String readFile() {
        return "{\"action\":\"read_file\",\"arguments\":{\"path\":\"a.txt\"}}";
    }

    private static String runEcho() {
        return "{\"action\":\"run_command\",\"arguments\":{\"cmd\":\"echo\","
                + "\"args\":[\"hi\"]}}";
    }

    private static String done() {
        return "{\"done\":true,\"summary\":\"ok\"}";
    }

    private RealTaskRunner buildRunner(List<String> responses,
                                       ApprovalPort approval) {
        return buildRunner(new ScriptedModel(responses), approval);
    }

    private RealTaskRunner buildRunner(ScriptedModel model,
                                       ApprovalPort approval) {
        KernelToolExecutor tools = new KernelToolExecutor(
                jail, Map.of("echo", echoBinary));
        SqliteStoragePort storage = new SqliteStoragePort(
                tempDir.resolve("steps.db"));
        return new RealTaskRunner(model, tools, storage, audit, approval,
                RISKS, TENANT, tempDir);
    }

    private static TaskDefinition task(String id, String tool) {
        return task(id, tool, 5);
    }

    private static TaskDefinition task(String id, String tool, int maxSteps) {
        return new TaskDefinition(id, "hitl test", "do it", maxSteps,
                "m", List.of(tool), List.of(), List.of(), List.of());
    }

    /**
     * Finds a working echo binary. Falls back to a PATH lookup because
     * /bin/echo does not exist on Android, where these tests also run.
     */
    private static Path findEcho() throws IOException {
        for (String candidate : new String[] {"/bin/echo", "/usr/bin/echo"}) {
            Path p = Path.of(candidate);
            if (Files.isExecutable(p)) {
                return p;
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) {
                if (dir.isBlank()) {
                    continue;
                }
                Path p = Path.of(dir).resolve("echo");
                if (Files.isExecutable(p)) {
                    return p;
                }
            }
        }
        throw new IllegalStateException("echo not found");
    }
}
