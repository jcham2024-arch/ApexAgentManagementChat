package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.core.port.model.AuditEntry;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.model.PlanRequest;
import com.apex.engine.core.port.model.PlanResult;
import com.apex.engine.core.port.model.SecretRef;
import com.apex.engine.core.port.model.StepLogEntry;
import com.apex.engine.core.port.model.TelemetrySpan;
import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;
import com.apex.engine.core.port.model.VectorHit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PortTest {

    @Test
    void chatModelPortIsImplementable() {
        ChatModelPort port = req -> new ChatResponse("ok");

        assertThat(port.chat(new ChatRequest("m", List.of("hi"))).content())
                .isEqualTo("ok");
    }

    @Test
    void plannerPortIsImplementable() {
        PlannerPort port = req -> new PlanResult(req.availableActionIds());

        assertThat(port.plan(new PlanRequest("goal", List.of("read")))
                .chosenActionIds()).containsExactly("read");
    }

    @Test
    void toolExecutorPortIsImplementable() {
        ToolExecutorPort port =
                call -> new ToolResult(call.id(), false, "out", 1L);

        assertThat(port.execute(new ToolCall("c1", "read", "{}")).output())
                .isEqualTo("out");
    }

    @Test
    void approvalPortIsImplementable() {
        ApprovalPort port =
                req -> new ApprovalDecision(req.id(), true, "approved");

        assertThat(port.request(new ApprovalRequest("r1", "write file", "L1"))
                .approved()).isTrue();
    }

    @Test
    void storagePortIsImplementable() {
        List<StepLogEntry> stored = new ArrayList<>();
        StoragePort port = new StoragePort() {
            @Override public void appendStep(StepLogEntry entry) {
                stored.add(entry);
            }

            @Override public List<StepLogEntry> stepsFor(String sessionId) {
                return List.copyOf(stored);
            }
        };

        port.appendStep(new StepLogEntry("s1", "session", 0, "plan", 2L));

        assertThat(port.stepsFor("session")).hasSize(1);
    }

    @Test
    void vectorStorePortIsImplementable() {
        VectorStorePort port = new VectorStorePort() {
            @Override public void store(String id, float[] vector) {
                // A no-op store is a valid implementation for this test.
            }

            @Override public List<VectorHit> search(float[] query, int k) {
                return List.of(new VectorHit("v1", 0.5d));
            }
        };

        assertThat(port.search(new float[] {1.0f}, 1)).hasSize(1);
    }

    @Test
    void secretsPortIsImplementable() {
        SecretsPort port = ref -> "value-for-" + ref.name();

        assertThat(port.get(new SecretRef("api-key")))
                .isEqualTo("value-for-api-key");
    }

    @Test
    void auditPortIsImplementable() {
        List<AuditEntry> log = new ArrayList<>();
        AuditPort port = new AuditPort() {
            @Override public void append(AuditEntry entry) {
                log.add(entry);
            }

            @Override public List<AuditEntry> all() {
                return List.copyOf(log);
            }
        };

        port.append(new AuditEntry("a1", "login", Instant.EPOCH, "hash"));

        assertThat(port.all()).hasSize(1);
    }

    @Test
    void telemetryPortIsImplementable() {
        AtomicInteger spans = new AtomicInteger();
        TelemetryPort port = span -> spans.incrementAndGet();

        port.record(new TelemetrySpan("plan", 3L));

        assertThat(spans).hasValue(1);
    }
}
