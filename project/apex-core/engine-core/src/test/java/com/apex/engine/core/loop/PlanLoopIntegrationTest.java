package com.apex.engine.core.loop;

import com.apex.engine.core.PolicyEngine;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatEventSink;
import com.apex.engine.core.port.CheckpointPort;
import com.apex.engine.core.port.model.AgentIdentity;
import com.apex.engine.core.port.model.AgentVersionManifest;
import com.apex.engine.core.port.model.CheckpointRecord;
import com.apex.engine.core.port.model.GuardrailSet;
import com.apex.engine.core.port.model.InvariantSet;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.ActionOutput;
import com.apex.engine.planner.Blackboard;
import com.apex.engine.planner.concurrent.CancellationToken;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
import static com.apex.engine.core.loop.LoopTestDoubles.CapturingSink;
import static com.apex.engine.core.loop.LoopTestDoubles.FakeAuditPort;
import static com.apex.engine.core.loop.LoopTestDoubles.FakeCheckpointPort;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PlanLoop}: checkpoint/resume, per-step
 * event emission, deterministic failure reporting, and cross-run
 * determinism with identical inputs.
 */
@Tag("integration")
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
        assertThat(result.steps().get(0).actionId()).isEqualTo("reaches-goal");
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
        assertThat(result.steps().get(0).actionId()).isEqualTo("failing");
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

        PlanLoopResult first = loop.run(Blackboard.empty(), done(),
                List.of(diverging(executions)), 2,
                new CancellationToken(), ChatEventSink.NOOP);

        assertThat(first.status()).isEqualTo(PlanStatus.MAX_STEPS_EXCEEDED);
        assertThat(first.steps()).hasSize(2);
        List<String> ids = first.steps().stream().map(StepRecord::actionId)
                .toList();
        assertThat(ids).containsExactly("diverging", "diverging");

        Optional<CheckpointRecord> restored = checkpoints.restoreCheckpoint("sess-1");
        assertThat(restored).isPresent();

        // Resume from the checkpointed state with an empty action list:
        // the goal is already satisfied, so no steps run.
        Blackboard resumed = new Blackboard(Map.of("done", "true"));
        PlanLoopResult second = loop.run(resumed, done(), List.of(),
                1, new CancellationToken(), ChatEventSink.NOOP);

        assertThat(second.status()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(second.steps()).isEmpty();
    }

    @Test
    void identicalInputsProduceIdenticalPlansAndAudit() {
        FakeAuditPort firstAudit = new FakeAuditPort();
        FakeAuditPort secondAudit = new FakeAuditPort();

        PlanLoopResult first = runWithAudit(firstAudit);
        PlanLoopResult second = runWithAudit(secondAudit);

        assertThat(first.status()).isEqualTo(second.status());
        assertThat(first.steps()).hasSameSizeAs(second.steps());
        assertThat(first.finalState().snapshot())
                .isEqualTo(second.finalState().snapshot());
        assertThat(firstAudit.all()).hasSameSizeAs(secondAudit.all());
        assertThat(firstAudit.all().get(0).action())
                .isEqualTo(secondAudit.all().get(0).action());
    }

    private static PlanLoopResult runWithAudit(FakeAuditPort audit) {
        PlanLoop loop = newLoop(audit, null);
        return loop.run(Blackboard.empty(), done(), List.of(reachingGoal()),
                5, new CancellationToken(), ChatEventSink.NOOP);
    }
}
