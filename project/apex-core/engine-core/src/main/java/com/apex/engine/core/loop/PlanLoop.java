package com.apex.engine.core.loop;

import com.apex.engine.core.PolicyEngine;
import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatEventSink;
import com.apex.engine.core.port.CheckpointPort;
import com.apex.engine.core.port.GatePlacementPolicy;
import com.apex.engine.core.port.MetricsPort;
import com.apex.engine.core.port.RuntimePolicy;
import com.apex.engine.core.port.TelemetryPort;
import com.apex.engine.core.port.ToolGatewayPort;
import com.apex.engine.core.port.model.AgentIdentity;
import com.apex.engine.core.port.model.AgentVersionManifest;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.core.port.model.AuthorizationRequest;
import com.apex.engine.core.port.model.CheckpointRecord;
import com.apex.engine.core.port.model.CostModel;
import com.apex.engine.core.port.model.GatewayDecision;
import com.apex.engine.core.port.model.GuardrailSet;
import com.apex.engine.core.port.model.InvariantSet;
import com.apex.engine.core.port.model.PolicyDecision;
import com.apex.engine.core.port.model.TelemetrySpan;
import com.apex.engine.core.port.model.ToolId;
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
 * <p>Before each action executes, if a {@link GatePlacementPolicy} is
 * installed and requires a gate for that action, the call is routed
 * through the {@link ToolGatewayPort}. A {@link GatewayDecision#Deny}
 * halts the loop with {@link PlanStatus#POLICY_DENIED}; a
 * {@link GatewayDecision#RequireApproval} halts it with
 * {@link PlanStatus#APPROVAL_REQUIRED}. Only an
 * {@link GatewayDecision#Approve} lets execution proceed.
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
    private final ToolGatewayPort gateway;
    private final GatePlacementPolicy gatePolicy;
    private final AgentIdentity identity;
    private final CheckpointPort checkpoint;
    private final String sessionId;
    private CostModel costModel = CostModel.none();

    public PlanLoop() {
        this(PolicyEngine.permissive(), GuardrailSet.none(),
                InvariantSet.empty(), null, null, null, null,
                null, null, null, null, null);
    }

    public PlanLoop(RuntimePolicy policy, GuardrailSet guardrails,
                    InvariantSet invariants, AuditPort audit,
                    AgentVersionManifest manifest, TelemetryPort telemetry,
                    MetricsPort metrics) {
        this(policy, guardrails, invariants, audit, manifest,
                telemetry, metrics, null, null, null, null, null);
    }

    public PlanLoop(RuntimePolicy policy, GuardrailSet guardrails,
                    InvariantSet invariants, AuditPort audit,
                    AgentVersionManifest manifest, TelemetryPort telemetry,
                    MetricsPort metrics, ToolGatewayPort gateway,
                    GatePlacementPolicy gatePolicy, AgentIdentity identity) {
        this(policy, guardrails, invariants, audit, manifest,
                telemetry, metrics, gateway, gatePolicy, identity,
                null, null);
    }

    public PlanLoop(RuntimePolicy policy, GuardrailSet guardrails,
                    InvariantSet invariants, AuditPort audit,
                    AgentVersionManifest manifest, TelemetryPort telemetry,
                    MetricsPort metrics, ToolGatewayPort gateway,
                    GatePlacementPolicy gatePolicy, AgentIdentity identity,
                    CheckpointPort checkpoint, String sessionId) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.guardrails = Objects.requireNonNull(guardrails, "guardrails");
        this.invariants = Objects.requireNonNull(invariants, "invariants");
        this.audit = audit;
        this.manifest = manifest;
        this.telemetry = telemetry;
        this.metrics = metrics;
        this.gateway = gateway;
        this.gatePolicy = gatePolicy;
        this.identity = identity;
        this.checkpoint = checkpoint;
        this.sessionId = sessionId;
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

            // Route tool calls through the gateway when a placement policy
            // requires an approval gate. Fail-closed: denials halt the loop,
            // require-approval stops it for out-of-loop human gating.
            if (gateway != null && gatePolicy != null
                    && gatePolicy.gateRequired(action, stepIndex, plan)) {
                AuthorizationRequest request = new AuthorizationRequest(
                        identity, new ToolId(action.id()), "{}", stepIndex);
                GatewayDecision decision = gateway.execute(request);
                if (decision instanceof GatewayDecision.Deny d) {
                    recordDenial(plan, d.reason());
                    return new PlanLoopResult(PlanStatus.POLICY_DENIED, state, steps);
                }
                if (decision instanceof GatewayDecision.RequireApproval r) {
                    recordApprovalRequired(plan, r.request());
                    return new PlanLoopResult(PlanStatus.APPROVAL_REQUIRED, state, steps);
                }
                // GatewayDecision.Approve: proceed to execute.
            }

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

            // Checkpoint after each successful step for deterministic replay.
            if (checkpoint != null && sessionId != null) {
                checkpoint.saveCheckpoint(sessionId, stepIndex + 1,
                        state.snapshot().toString());
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

    private void recordApprovalRequired(Plan plan, ApprovalRequest request) {
        if (audit != null) {
            String manifestRef = manifest == null ? "none"
                    : "seq=" + manifest.sequence();
            audit.append("APPROVAL_REQUIRED: request=" + request.id()
                    + " | summary=" + request.summary()
                    + " | manifest=" + manifestRef
                    + " | step=" + plan.steps().size(), Instant.now());
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
