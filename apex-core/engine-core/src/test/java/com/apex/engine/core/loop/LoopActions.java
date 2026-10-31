package com.apex.engine.core.loop;

import com.apex.engine.core.concurrent.CancellationToken;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.ActionOutput;
import com.apex.engine.planner.Blackboard;
import com.apex.engine.planner.Condition;
import com.apex.engine.planner.FactCondition;
import com.apex.engine.planner.Goal;
import com.apex.engine.planner.ImmutableBlackboard;
import com.apex.engine.planner.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Fake actions for the {@code PlanLoop} tests. Each one isolates a
 * single loop path: reach the goal, never reach it, fail, diverge, or
 * work long enough to be cancelled.
 */
final class LoopActions {

    private LoopActions() {
    }

    static Goal done() {
        return new Goal("g", List.of(new FactCondition("done", "true")));
    }

    static PlanLoopResult run(int maxSteps, CancellationToken token,
                              List<Action> actions) {
        return new PlanLoop().run(ImmutableBlackboard.empty(), done(), actions,
                maxSteps, token);
    }

    /** One step satisfies the goal. */
    static Action reachingGoal() {
        return new FakeAction("reaches-goal", "done", "true", Mode.SUCCESS);
    }

    /** Effects never satisfy the goal, so the planner finds no plan. */
    static Action neverReachingGoal() {
        return new FakeAction("never-reaches-goal", "other", "x", Mode.SUCCESS);
    }

    /** A plan exists, but execution reports failure. */
    static Action failing() {
        return new FakeAction("failing", "done", "true", Mode.FAILURE);
    }

    /**
     * Planning always sees a one-step path, but execution leaves the
     * state unchanged, so the goal is never reached by the loop.
     */
    static Action diverging(AtomicInteger executions) {
        return new FakeAction("diverging", "done", "true", Mode.UNCHANGED,
                executions);
    }

    /**
     * Simulates 500ms of work in 5ms slices. On cancellation it returns
     * the unchanged state, so the loop's own boundary check reports
     * CANCELLED rather than FAILED.
     */
    static Action slow() {
        return new Action() {
            @Override public String id() { return "slow"; }

            @Override public List<Condition> preconditions() { return List.of(); }

            @Override public Blackboard applyEffects(Blackboard state) {
                return state.withString("done", "true");
            }

            @Override public double cost() { return 1.0; }

            @Override public RiskLevel riskLevel() { return RiskLevel.L0; }

            @Override public ActionOutput execute(Blackboard state,
                                                  BooleanSupplier isCancelled) {
                Instant deadline = Instant.now().plusMillis(500L);
                while (Instant.now().isBefore(deadline)) {
                    if (isCancelled.getAsBoolean()) {
                        return new ActionOutput.Success(state);
                    }
                    try {
                        Thread.sleep(5L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new ActionOutput.Success(state);
                    }
                }
                return new ActionOutput.Success(applyEffects(state));
            }
        };
    }

    private enum Mode { SUCCESS, UNCHANGED, FAILURE }

    private static final class FakeAction implements Action {

        private final String id;
        private final String key;
        private final String value;
        private final Mode mode;
        private final AtomicInteger executions;

        FakeAction(String id, String key, String value, Mode mode) {
            this(id, key, value, mode, null);
        }

        FakeAction(String id, String key, String value, Mode mode,
                   AtomicInteger executions) {
            this.id = id;
            this.key = key;
            this.value = value;
            this.mode = mode;
            this.executions = executions;
        }

        @Override public String id() { return id; }

        @Override public List<Condition> preconditions() { return List.of(); }

        @Override public Blackboard applyEffects(Blackboard state) {
            return state.withString(key, value);
        }

        @Override public double cost() { return 1.0; }

        @Override public RiskLevel riskLevel() { return RiskLevel.L0; }

        @Override public ActionOutput execute(Blackboard state,
                                              BooleanSupplier isCancelled) {
            if (executions != null) {
                executions.incrementAndGet();
            }
            if (mode == Mode.FAILURE) {
                return new ActionOutput.Failure("refused");
            }
            if (mode == Mode.UNCHANGED) {
                return new ActionOutput.Success(state);
            }
            return new ActionOutput.Success(applyEffects(state));
        }
    }
}
