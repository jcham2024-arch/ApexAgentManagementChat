package com.apex.engine.core.loop;

import com.apex.engine.core.PolicyEngine;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatEventSink;
import com.apex.engine.core.port.RuntimePolicy;
import com.apex.engine.core.port.model.GuardrailSet;
import com.apex.engine.core.port.model.InvariantSet;
import com.apex.engine.core.port.model.PolicyDecision;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.ActionOutput;
import com.apex.engine.planner.AStarPlanner;
import com.apex.engine.planner.Blackboard;
import com.apex.engine.planner.concurrent.CancellationToken;
import com.apex.engine.planner.Goal;
import com.apex.engine.planner.Plan;

import java.time.Instant;
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
 * <p>Before the first step executes, the loop runs a fail-closed
 * pre-execution guard: {@link InvariantChecker} rejects structurally
 * unsafe plans, and the injected {@link RuntimePolicy} may deny a plan
 * deterministically. Denials are emitted to the audit chain and halt
 * the loop with {@link PlanStatus#POLICY_DENIED} rather than throwing.
 *
 * <p>The concrete planner ({@link AStarPlanner}) is wired at the
 * application level. {@link Action} lives in engine-planner.
 */
public final class PlanLoop {

    private final AStarPlanner planner = new AStarPlanner();
    private final RuntimePolicy policy;
    private final GuardrailSet guardrails;
    private final InvariantSet invariants;
    private final AuditPort audit;

    public PlanLoop() {
        this(PolicyEngine.permissive(), GuardrailSet.none(),
                InvariantSet.empty(), null);
    }

    public PlanLoop(RuntimePolicy policy, GuardrailSet guardrails,
                    InvariantSet invariants, AuditPort audit) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.guardrails = Objects.requireNonNull(guardrails, "guardrails");
        this.invariants = Objects.requireNonNull(invariants, "invariants");
        this.audit = audit;
    }

    public PlanLoopResult run(Blackboard initial, Goal goal,
                              List<Action> actions, int maxSteps,
                              CancellationToken token, ChatEventSink sink) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(sink, "sink");
        int effectiveMaxSteps = Math.min(maxSteps, guardrails.maxSteps());
        if (effectiveMaxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps <= 0");
        }

        Blackboard state = initial;
        List<StepRecord> steps = new ArrayList<>(effectiveMaxSteps);

        while (steps.size() < effectiveMaxSteps) {
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
            Plan plan = next.get();

            // Fail-closed pre-execution guard: invariants + policy.
            // Denials halt the loop deterministically with an audit entry
            // instead of throwing, so callers observe a named outcome.
            try {
                InvariantChecker.check(plan, invariants);
                PolicyDecision decision = policy.decide(plan);
                if (!decision.allowed()) {
                    recordDenial(plan, decision.reason());
                    return new PlanLoopResult(PlanStatus.POLICY_DENIED, state, steps);
                }
            } catch (IllegalArgumentException e) {
                recordDenial(plan, "invariant violation: " + e.getMessage());
                return new PlanLoopResult(PlanStatus.POLICY_DENIED, state, steps);
            }

            Action action = plan.steps().get(0);

            int stepIndex = steps.size();
            sink.emit("PLAN_STEP_STARTED", stepIndex, Instant.now());

            ActionOutput output = action.execute(state, token);
            steps.add(new StepRecord(stepIndex, action.id(), output));

            sink.emit("PLAN_STEP_COMPLETED", stepIndex, Instant.now());

            if (output instanceof ActionOutput.Failure) {
                return new PlanLoopResult(PlanStatus.FAILED, state, steps);
            }
            state = ((ActionOutput.Success) output).newState();
        }
        return new PlanLoopResult(PlanStatus.MAX_STEPS_EXCEEDED, state, steps);
    }

    private void recordDenial(Plan plan, String reason) {
        if (audit != null) {
            audit.append("PLAN_DENIED: " + reason + " | steps="
                    + plan.steps().size() + " | cost=" + plan.cost(),
                    Instant.now());
        }
    }
}
