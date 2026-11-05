package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatEventSink;
import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.model.AuditEntry;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.planner.RiskLevel;
import com.apex.engine.store.sqlite.SqliteStoragePort;
import com.apex.engine.tools.impl.KernelToolExecutor;
import com.apex.engine.tools.impl.RealPathJail;
import com.apex.platform.audit.InMemoryAuditLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealTaskRunnerAuditTest {

    private static final TenantId TENANT = new TenantId("t-audit");
    private static final byte[] AUDIT_KEY = key();
    private static final ApprovalPort APPROVE = new AutoApprove();
    private static final Map<String, RiskLevel> RISKS = Map.of(
            "read_file", RiskLevel.L0);

    private static byte[] key() {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) 0x42);
        return k;
    }

    @TempDir
    Path tempDir;

    RealPathJail jail;
    AuditPort audit;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        jail = new RealPathJail(tempDir);
        audit = new InMemoryAuditLog(AUDIT_KEY);
    }

    @Test
    void auditRecordsToolCall() {
        runner(List.of(
                "{\"action\":\"read_file\",\"arguments\":{\"path\":\"a.txt\"}}",
                "{\"done\":true,\"summary\":\"ok\"}")).run(task("t1"));

        List<AuditEntry> entries = audit.all();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).action()).isEqualTo("tool_call:read_file");
    }

    @Test
    void noToolCallsMeansNoAuditEntries() {
        runner(List.of("{\"done\":true,\"summary\":\"noop\"}")).run(task("t2"));

        assertThat(audit.all()).isEmpty();
    }

    @Test
    void chainVerifiesAfterRun() {
        runner(List.of(
                "{\"action\":\"read_file\",\"arguments\":{\"path\":\"a.txt\"}}",
                "{\"action\":\"read_file\",\"arguments\":{\"path\":\"a.txt\"}}",
                "{\"done\":true,\"summary\":\"ok\"}")).run(task("t3"));

        assertThat(audit.all()).hasSize(2);
        assertThat(audit.verifyChain()).isTrue();
    }

    private RealTaskRunner runner(List<String> responses) {
        KernelToolExecutor tools = new KernelToolExecutor(jail, Map.of());
        SqliteStoragePort storage = new SqliteStoragePort(
                tempDir.resolve("steps.db"));
        return new RealTaskRunner(new Scripted(responses), tools, storage,
                audit, APPROVE, ChatEventSink.NOOP, RISKS, TENANT, tempDir);
    }

    private TaskDefinition task(String id) {
        return new TaskDefinition(id, "test", "do it", 5, "m",
                List.of("read_file"), List.of(), List.of(), List.of());
    }

    /** Per-instance cursor, so test order cannot change the responses. */
    private static final class Scripted implements ChatModelPort {
        private final List<String> responses;
        private int next;

        Scripted(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            int index = Math.min(next++, responses.size() - 1);
            return new ChatResponse(responses.get(index));
        }
    }
}
