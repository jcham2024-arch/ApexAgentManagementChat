package com.apex.engine.core.loop;

import com.apex.engine.core.concurrent.CancellationToken;
import com.apex.engine.core.port.ChatEventSink;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.apex.engine.core.loop.LoopActions.diverging;
import static com.apex.engine.core.loop.LoopActions.failing;
import static com.apex.engine.core.loop.LoopActions.neverReachingGoal;
import static com.apex.engine.core.loop.LoopActions.reachingGoal;
import static com.apex.engine.core.loop.LoopActions.run;
import static org.assertj.core.api.Assertions.assertThat;

class PlanLoopTest {

    @Test
    void completesOnSatisfiedGoal() {
        PlanLoopResult result = run(5, new CancellationToken(),
                List.of(reachingGoal()), ChatEventSink.NOOP);

        assertThat(result.status()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(result.steps()).hasSize(1);
        assertThat(result.finalState().getString("done")).contains("true");
    }

    @Test
    void stuckWhenNoPlanPossible() {
        PlanLoopResult result = run(5, new CancellationToken(),
                List.of(neverReachingGoal()), ChatEventSink.NOOP);

        assertThat(result.status()).isEqualTo(PlanStatus.STUCK);
        assertThat(result.steps()).isEmpty();
    }

    @Test
    void failedWhenActionFails() {
        PlanLoopResult result = run(5, new CancellationToken(),
                List.of(failing()), ChatEventSink.NOOP);

        assertThat(result.status()).isEqualTo(PlanStatus.FAILED);
        assertThat(result.steps()).hasSize(1);
    }

    @Test
    void maxStepsStopsTheLoop() {
        AtomicInteger executions = new AtomicInteger();

        PlanLoopResult result = run(3, new CancellationToken(),
                List.of(diverging(executions)), ChatEventSink.NOOP);

        assertThat(result.status()).isEqualTo(PlanStatus.MAX_STEPS_EXCEEDED);
        assertThat(result.steps()).hasSize(3);
    }

    @Test
    void replansOncePerStep() {
        AtomicInteger executions = new AtomicInteger();

        PlanLoopResult result = run(3, new CancellationToken(),
                List.of(diverging(executions)), ChatEventSink.NOOP);

        assertThat(executions).hasValue(result.steps().size());
        assertThat(result.steps()).extracting(StepRecord::index)
                .containsExactly(0, 1, 2);
        assertThat(result.steps()).extracting(StepRecord::actionId)
                .containsOnly("diverging");
    }

    @Test
    void cancellationStopsLoopAfterOneStep() {
        CancellationToken token = new CancellationToken();
        AtomicInteger executions = new AtomicInteger();

        // Without cancellation, the loop runs all maxSteps steps.
        PlanLoopResult result = run(2, token,
                List.of(diverging(executions)), ChatEventSink.NOOP);

        assertThat(result.status()).isEqualTo(PlanStatus.MAX_STEPS_EXCEEDED);
        assertThat(result.steps()).hasSize(2);

        // Now cancel and re-run: the loop checks the token at each step
        // boundary and returns CANCELLED before executing any step.
        token.cancel();
        PlanLoopResult result2 = run(5, token,
                List.of(diverging(new AtomicInteger())), ChatEventSink.NOOP);
        assertThat(result2.status()).isEqualTo(PlanStatus.CANCELLED);
        assertThat(result2.steps()).hasSize(0);
    }
}
