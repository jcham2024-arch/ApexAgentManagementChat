package com.apex.engine.core.loop;

import com.apex.engine.core.PolicyEngine;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatEventSink;
import com.apex.engine.core.port.CheckpointPort;
import com.apex.engine.core.port.model.AgentIdentity;
import com.apex.engine.core.port.model.AgentVersionManifest;
import com.apex.engine.core.port.model.AuditEntry;
import com.apex.engine.core.port.model.CheckpointRecord;
import com.apex.engine.core.port.model.GuardrailSet;
import com.apex.engine.core.port.model.InvariantSet;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.ActionOutput;
import com.apex.engine.planner.Blackboard;
import com.apex.engine.planner.concurrent.CancellationToken;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.apex.engine.core.loop.LoopActions.diverging;
import static com.apex.engine.core.loop.LoopActions.done;
import static com.apex.engine.core.loop.LoopActions.failing;
import static com.apex.engine.core.loop.LoopActions.reachingGoal;
import static com.apex.engine.core.loop.LoopActions.run;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PlanLoop}: checkpoint/resume, per-step
 * event emission, deterministic failure reporting, and cross-run
 * determinism with identical inputs.
 */
class PlanLoopIntegrationTest {

    private static final AgentIdentity IDENTITY = new AgentIdentity(
            "agent-1", "owner-1", "tenant-1", "test-agent", Set.of());

    private static final AgentVersionManifest MANIFEST = new AgentVersionManifest(
            "m-1", "model-a", "prompt-hash", "tool-hash", "eval-0", "none", 0);

    private static PlanLoop newLoop(AuditPort audit, CheckpointPort checkpoint) {
        return new PlanLoop(PolicyEngine.permissive(), GuardrailSet.none(),
                InvariantSet.empty(), audit, MANIFEST, null, null, null,
                null, IDENTITY, checkpoint, "sess-1");
    }

    @Test
    void successfulExecutionEmitsStepEvents() {
        CapturingSink sink = new CapturingSink();
        PlanLoopResult result = run(5, new CancellationToken(),
                List.of(reachingGoal()), sink);

        assertThat(result.status()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(result.steps()).hasSize(1);
        assertThat(result.finalState().getString("done")).contains("true");
        assertThat(sink.types).containsExactly("PLAN_STEP_STARTED",
                "PLAN_STEP_COMPLETED");
    }

    @Test
    void failureReportsDeterministicOutcome() {
        PlanLoopResult result = run(5, new CancellationToken(),
                List.of(failing()), ChatEventSink.NOOP);

        assertThat(result.status()).isEqualTo(PlanStatus.FAILED);
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).output())
                .isInstanceOf(ActionOutput.Failure.class);
    }

    @Test
    void checkpointCreatedAndRestored() {
        FakeCheckpointPort checkpoints = new FakeCheckpointPort();
        PlanLoop loop = newLoop(null, checkpoints);
        AtomicInteger executions = new AtomicInteger();

        PlanLoopResult result = loop.run(Blackboard.empty(), done(),
                List.of(diverging(executions)), 2,
                new CancellationToken(), ChatEventSink.NOOP);

        assertThat(result.status()).isEqualTo(PlanStatus.MAX_STEPS_EXCEEDED);
        assertThat(result.steps()).hasSize(2);
        assertThat(executions.get()).isEqualTo(2);
        // The port replaces any existing checkpoint for the same session,
        // so only the latest (step 2) is retained.
        assertThat(checkpoints.all()).hasSize(1);
        assertThat(checkpoints.all().get(0).stepIndex()).isEqualTo(2);

        Optional<CheckpointRecord> restored = checkpoints.restoreCheckpoint("sess-1");
        assertThat(restored).isPresent();
        assertThat(restored.get().stepIndex()).isEqualTo(2);

        checkpoints.deleteCheckpoint("sess-1");
        assertThat(checkpoints.restoreCheckpoint("sess-1")).isEmpty();
    }

    @Test
    void resumeFromCheckpointCompletes() {
        FakeCheckpointPort checkpoints = new FakeCheckpointPort();
        PlanLoop loop = newLoop(null, checkpoints);
        AtomicInteger executions = new AtomicInteger();

        loop.run(Blackboard.empty(), done(),
                List.of(diverging(executions)), 1,
                new CancellationToken(), ChatEventSink.NOOP);

        Optional<CheckpointRecord> restored = checkpoints.restoreCheckpoint("sess-1");
        assertThat(restored).isPresent();

        Blackboard resumed = new Blackboard(new HashMap<>(Map.of("done", "true")));
        PlanLoopResult result = loop.run(resumed, done(), List.of(),
                1, new CancellationToken(), ChatEventSink.NOOP);

        assertThat(result.status()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(result.steps()).isEmpty();
    }

    @Test
    void identicalInputsProduceIdenticalPlansAndAudit() {
        FakeAuditPort firstAudit = new FakeAuditPort();
        FakeAuditPort secondAudit = new FakeAuditPort();

        PlanLoopResult first = runWithAudit(firstAudit);
        PlanLoopResult second = runWithAudit(secondAudit);

        assertThat(first.status()).isEqualTo(second.status());
        assertThat(first.steps()).hasSameSizeAs(second.steps());
        assertThat(firstAudit.all()).hasSameSizeAs(secondAudit.all());
        assertThat(firstAudit.all().get(0).action())
                .isEqualTo(secondAudit.all().get(0).action());
    }

    private static PlanLoopResult runWithAudit(FakeAuditPort audit) {
        PlanLoop loop = newLoop(audit, null);
        return loop.run(Blackboard.empty(), done(), List.of(reachingGoal()),
                5, new CancellationToken(), ChatEventSink.NOOP);
    }

    /** Captures event types and sequences emitted by the loop. */
    static final class CapturingSink implements ChatEventSink {
        final List<String> types = new ArrayList<>();
        final List<Long> sequences = new ArrayList<>();

        @Override
        public void emit(String type, long sequence, Instant at) {
            types.add(type);
            sequences.add(sequence);
        }
    }

    /** In-memory audit port recording every appended entry. */
    static final class FakeAuditPort implements AuditPort {
        final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public AuditEntry append(String action, Instant at, String traceId,
                                 String spanId) {
            AuditEntry entry = new AuditEntry("e", action, at, "", "h",
                    traceId, spanId);
            entries.add(entry);
            return entry;
        }

        @Override
        public List<AuditEntry> all() {
            return List.copyOf(entries);
        }

        @Override
        public boolean verifyChain() {
            return true;
        }
    }

    /** In-memory checkpoint port keyed by session id. */
    static final class FakeCheckpointPort implements CheckpointPort {
        private final Map<String, CheckpointRecord> store = new HashMap<>();

        @Override
        public void saveCheckpoint(String sessionId, int stepIndex,
                                   String stateSnapshot) {
            store.put(sessionId, new CheckpointRecord(sessionId, stepIndex,
                    stateSnapshot, Instant.now()));
        }

        @Override
        public Optional<CheckpointRecord> restoreCheckpoint(String sessionId) {
            return Optional.ofNullable(store.get(sessionId));
        }

        @Override
        public void deleteCheckpoint(String sessionId) {
            store.remove(sessionId);
        }

        List<CheckpointRecord> all() {
            return List.copyOf(store.values());
        }
    }
}
