package com.apex.engine.core.port;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.core.port.model.AuditEntry;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.DeploymentRouter;
import com.apex.engine.core.port.EvaluationProbe;
import com.apex.engine.core.port.model.DeploymentSlot;
import com.apex.engine.core.port.model.EvaluationProbeResult;
import com.apex.engine.core.port.model.PlanRequest;
import com.apex.engine.core.port.model.PlanResult;
import com.apex.engine.core.port.model.PlanTrace;
import com.apex.engine.core.port.model.SecretRef;
import com.apex.engine.core.port.model.SkillManifest;
import com.apex.engine.core.port.model.StepLogEntry;
import com.apex.engine.core.port.model.TelemetrySpan;
import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;
import com.apex.engine.core.port.model.TrafficPolicy;
import com.apex.engine.core.port.model.VectorHit;
import com.apex.engine.core.port.model.WorkspaceArtifact;
import com.apex.engine.planner.RiskLevel;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                req -> new ApprovalDecision.Approve(req.id(), "approved");

        assertThat(port.request(new ApprovalRequest("r1", "write file",
                RiskLevel.L1)).approved()).isTrue();
    }

    @Test
    void storagePortIsImplementable() {
        List<StepLogEntry> stored = new ArrayList<>();
        StoragePort port = new StoragePort() {
            @Override public void appendStep(StepLogEntry entry) {
                stored.add(entry);
            }

            @Override public List<StepLogEntry> stepsFor(TenantId tenantId,
                                                         String sessionId) {
                return List.copyOf(stored);
            }

            @Override public void clear(TenantId tenantId, String sessionId) {
                stored.clear();
            }
        };

        port.appendStep(new StepLogEntry("s1", new TenantId("t"), "session",
                0, "plan", 2L));

        assertThat(port.stepsFor(new TenantId("t"), "session")).hasSize(1);
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
        AtomicInteger spans = new AtomicInteger();
        AtomicInteger traces = new AtomicInteger();
        AuditPort port = new AuditPort() {
            private final List<AuditEntry> entries = new ArrayList<>();

            @Override public AuditEntry append(String action, Instant at) {
                return append(action, at, "", "");
            }

            @Override public AuditEntry append(String action, Instant at,
                    String traceId, String spanId) {
                AuditEntry e = new AuditEntry(
                        java.util.UUID.randomUUID().toString(),
                        action, at, "", "hash", traceId, spanId);
                entries.add(e);
                traces.incrementAndGet();
                return e;
            }

            @Override public List<AuditEntry> all() {
                return List.copyOf(entries);
            }

            @Override public boolean verifyChain() {
                return true;
            }
        };

        AuditEntry entry = port.append("x", Instant.now(), "trace-1", "span-1");

        assertThat(entry.action()).isEqualTo("x");
        assertThat(entry.traceId()).isEqualTo("trace-1");
        assertThat(entry.spanId()).isEqualTo("span-1");
        assertThat(port.all()).hasSize(1);
        assertThat(port.verifyChain()).isTrue();
        assertThat(traces).hasValue(1);
    }

    @Test
    void telemetryPortIsImplementable() {
        AtomicInteger spans = new AtomicInteger();
        TelemetryPort port = span -> spans.incrementAndGet();

        port.record(new TelemetrySpan("plan", 3L));

        assertThat(spans).hasValue(1);
    }

    @Test
    void skillResolverPortIsImplementable() {
        SkillResolver port = new SkillResolver() {
            @Override public java.util.Optional<SkillManifest> resolve(
                    String skillId) {
                return java.util.Optional.of(new SkillManifest(
                        skillId, "test-skill", "1.0", "test desc",
                        "interface-spec", "exec-ref", List.of(),
                        Map.of()));
            }

            @Override public List<String> listSkills() {
                return List.of("skill-1");
            }
        };

        assertThat(port.resolve("skill-1")).isPresent();
        assertThat(port.resolve("skill-1").get().skillId())
                .isEqualTo("skill-1");
        assertThat(port.listSkills()).containsExactly("skill-1");
    }

    @Test
    void workspacePortIsImplementable() {
        WorkspacePort port = new WorkspacePort() {
            @Override public WorkspaceArtifact writeArtifact(
                    String sessionId, String artifactId, String content) {
                return new WorkspaceArtifact(sessionId, artifactId, "v1",
                        content, Instant.now());
            }

            @Override public WorkspaceArtifact readArtifact(
                    String sessionId, String artifactId, String version) {
                return new WorkspaceArtifact(sessionId, artifactId, version,
                        "content", Instant.now());
            }

            @Override public List<String> listArtifacts(String sessionId) {
                return List.of("artifact-1");
            }

            @Override public void deleteArtifact(String sessionId,
                    String artifactId, String version) {
                // no-op for test
            }
        };

        WorkspaceArtifact written = port.writeArtifact("s1", "a1", "content");
        assertThat(written.sessionId()).isEqualTo("s1");
        assertThat(written.artifactId()).isEqualTo("a1");

        WorkspaceArtifact read = port.readArtifact("s1", "a1", "v1");
        assertThat(read.content()).isEqualTo("content");

        assertThat(port.listArtifacts("s1")).containsExactly("artifact-1");
    }

    @Test
    void deploymentRouterPortIsImplementable() {
        DeploymentRouter port = new DeploymentRouter() {
            @Override public DeploymentSlot route(String sessionId,
                    TrafficPolicy policy) {
                return policy.route(sessionId);
            }

            @Override public DeploymentSlot currentSlot() {
                return DeploymentSlot.PRODUCTION;
            }
        };

        TrafficPolicy policy = new TrafficPolicy(50.0, "murmur3");
        assertThat(port.route("session-1", policy))
                .isIn(DeploymentSlot.STAGING, DeploymentSlot.PRODUCTION);
        assertThat(port.currentSlot())
                .isEqualTo(DeploymentSlot.PRODUCTION);
    }

    @Test
    void evaluationProbePortIsImplementable() {
        EvaluationProbe port = new EvaluationProbe() {
            @Override public EvaluationProbeResult evaluate(PlanTrace trace) {
                return new EvaluationProbeResult("test-probe", 0.8, true,
                        "good", Map.of());
            }

            @Override public String name() {
                return "test-probe";
            }
        };

        PlanTrace trace = new PlanTrace("session-1", "manifest-1",
                List.of(), null);
        EvaluationProbeResult result = port.evaluate(trace);

        assertThat(result.probeName()).isEqualTo("test-probe");
        assertThat(result.score()).isEqualTo(0.8);
        assertThat(result.passed()).isTrue();
    }
}
