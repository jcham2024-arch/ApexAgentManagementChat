package com.apex.engine.core.loop;

import com.apex.engine.core.PolicyEngine;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatEventSink;
import com.apex.engine.core.port.MetricsPort;
import com.apex.engine.core.port.RuntimePolicy;
import com.apex.engine.core.port.TelemetryPort;
import com.apex.engine.core.port.model.AgentVersionManifest;
import com.apex.engine.core.port.model.CostModel;
import com.apex.engine.core.port.model.GuardrailSet;
import com.apex.engine.core.port.model.InvariantSet;
import com.apex.engine.core.port.model.PolicyDecision;
import com.apex.engine.core.port.model.TelemetrySpan;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.ActionOutput;
import com.apex.engine.planner.AStarPlanner;
import com.apex.engine.planner.Blackboard;
import com.apex.engine.planner.concurrent.CancellationToken;
import com.apex.engine.planner.Goal;
import com.apex.engine.planner.Plan;

import java.time.Duration;
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
    private final AgentVersionManifest manifest;
    private final TelemetryPort telemetry;
    private final MetricsPort metrics;
    private CostModel costModel = CostModel.none();

    public PlanLoop() {
        this(PolicyEngine.permissive(), GuardrailSet.none(),
                InvariantSet.empty(), null, null, null, null);
    }

    public PlanLoop(RuntimePolicy policy, GuardrailSet guardrails,
                    InvariantSet invariants, AuditPort audit,
                    AgentVersionManifest manifest, TelemetryPort telemetry,
                    MetricsPort metrics) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.guardrails = Objects.requireNonNull(guardrails, "guardrails");
        this.invariants = Objects.requireNonNull(invariants, "invariants");
        this.audit = audit;
        this.manifest = manifest;
        this.telemetry = telemetry;
        this.metrics = metrics;
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

        // Pin the resolved manifest at session start; the plan carries it
        // for its entire duration so two sessions with the same manifest
        // are reproducible.
        if (manifest != null && audit != null) {
            audit.append("MANIFEST_PINNED: seq=" + manifest.sequence()
                    + " | model=" + manifest.modelConfigRef()
                    + " | tools=" + manifest.toolSetHash()
                    + " | eval=" + manifest.evalBaselineRef(),
                    Instant.now());
        }

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
            Instant stepStart = Instant.now();
            sink.emit("PLAN_STEP_STARTED", stepIndex, stepStart);

            ActionOutput output = action.execute(state, token);
            steps.add(new StepRecord(stepIndex, action.id(), output));

            Instant stepEnd = Instant.now();
            long durationMs = Duration.between(stepStart, stepEnd).toMillis();
            sink.emit("PLAN_STEP_COMPLETED", stepIndex, stepEnd);

            // Observe every step: telemetry span + metrics counter/histogram.
            if (telemetry != null) {
                telemetry.record(new TelemetrySpan("PLAN_STEP_" + stepIndex,
                        durationMs));
            }
            if (metrics != null) {
                metrics.counter("steps.executed", 1L);
                metrics.histogram("step.duration.ms", (double) durationMs);
            }

            if (output instanceof ActionOutput.Failure) {
                return new PlanLoopResult(PlanStatus.FAILED, state, steps);
            }
            state = ((ActionOutput.Success) output).newState();
        }
        return new PlanLoopResult(PlanStatus.MAX_STEPS_EXCEEDED, state, steps);
    }

    private void recordDenial(Plan plan, String reason) {
        if (audit != null) {
            String manifestRef = manifest == null ? "none"
                    : "seq=" + manifest.sequence();
            audit.append("PLAN_DENIED: " + reason + " | manifest=" + manifestRef
                    + " | steps=" + plan.steps().size()
                    + " | cost=" + plan.cost(), Instant.now());
        }
    }

    /** Accumulates per-step token + cost into the running session cost. */
    public void accumulateCost(CostModel cost) {
        Objects.requireNonNull(cost, "cost");
        this.costModel = new CostModel(
                this.costModel.promptTokens().add(cost.promptTokens()),
                this.costModel.completionTokens().add(cost.completionTokens()),
                this.costModel.promptCostCents().add(cost.promptCostCents()),
                this.costModel.completionCostCents().add(cost.completionCostCents()));
    }

    /** Returns the accumulated cost for this plan/session. */
    public CostModel costModel() {
        return costModel;
    }
}
