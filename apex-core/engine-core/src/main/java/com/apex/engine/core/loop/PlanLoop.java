package com.apex.engine.core.loop;

import com.apex.engine.core.concurrent.CancellationToken;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.ActionOutput;
import com.apex.engine.planner.AStarPlanner;
import com.apex.engine.planner.Blackboard;
import com.apex.engine.planner.Goal;
import com.apex.engine.planner.Plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * OODA replan loop. Plans from the current blackboard to the goal,
 * executes the first action, applies its effects, and repeats. Checks
 * the cancellation token at every step boundary so cancellation is
 * observable within 100ms (K6). Stops at {@code maxSteps}.
 *
 * <p>The action receives {@code token::isCancelled} rather than the
 * token itself: {@link Action} lives in engine-planner, which cannot
 * name this module's {@link CancellationToken}. See the note on that
 * class for the fix.
 */
public final class PlanLoop {

    private final AStarPlanner planner = new AStarPlanner();

    public PlanLoopResult run(Blackboard initial, Goal goal,
                              List<Action> actions, int maxSteps,
                              CancellationToken token) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(token, "token");
        if (maxSteps <= 0) throw new IllegalArgumentException("maxSteps <= 0");

        Blackboard state = initial;
        List<StepRecord> steps = new ArrayList<>(maxSteps);

        while (steps.size() < maxSteps) {
            if (token.isCancelled()) {
                return new PlanLoopResult(PlanStatus.CANCELLED, state, steps);
            }
            if (goal.satisfiedBy(state)) {
                return new PlanLoopResult(PlanStatus.COMPLETED, state, steps);
            }

            Optional<Plan> next = planner.plan(state, goal, actions);
            if (next.isEmpty()) {
                return new PlanLoopResult(PlanStatus.STUCK, state, steps);
            }
            Action action = next.get().steps().get(0);

            ActionOutput output = action.execute(state, token::isCancelled);
            steps.add(new StepRecord(steps.size(), action.id(), output));

            if (output instanceof ActionOutput.Failure) {
                return new PlanLoopResult(PlanStatus.FAILED, state, steps);
            }
            state = ((ActionOutput.Success) output).newState();
        }
        return new PlanLoopResult(PlanStatus.MAX_STEPS_EXCEEDED, state, steps);
    }
}
