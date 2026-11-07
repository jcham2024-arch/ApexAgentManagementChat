package com.apex.engine.core.loop;

import com.apex.engine.core.port.GatePlacementPolicy;
import com.apex.engine.core.port.ToolGatewayPort;
import com.apex.engine.core.port.model.AgentIdentity;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.core.port.model.AuthorizationRequest;
import com.apex.engine.core.port.model.GatewayDecision;
import com.apex.engine.core.port.model.ToolId;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.ActionOutput;
import com.apex.engine.planner.Blackboard;
import com.apex.engine.planner.concurrent.CancellationToken;
import com.apex.engine.planner.Condition;
import com.apex.engine.planner.FactCondition;
import com.apex.engine.planner.Goal;
import com.apex.engine.planner.RiskLevel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.apex.engine.core.loop.LoopActions.diverging;
import static com.apex.engine.core.loop.LoopActions.done;
import static com.apex.engine.core.loop.LoopActions.run;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link PlanLoop} routes tool calls through the
 * {@link ToolGatewayPort}: denials halt the loop with
 * {@link PlanStatus#POLICY_DENIED}, require-approval stops it with
 * {@link PlanStatus#APPROVAL_REQUIRED}, and approvals let execution
 * proceed.
 */
class PlanLoopGatewayTest {

    private static final AgentIdentity IDENTITY = new AgentIdentity(
            "agent-1", "owner-1", "tenant-1", "test-agent",
            java.util.Set.of("tool:*"));

    private static final Goal GOAL = done();

    private static final class GatePolicy implements GatePlacementPolicy {
        private final java.util.Set<String> gatedToolIds;

        GatePolicy(java.util.Set<String> gatedToolIds) {
            this.gatedToolIds = java.util.Set.copyOf(gatedToolIds);
        }

        @Override
        public boolean gateRequired(Action action, int stepIndex,
                                    com.apex.engine.planner.Plan plan) {
            return gatedToolIds.contains(action.id());
        }
    }

    private static final class StubGateway implements ToolGatewayPort {
        private final GatewayDecision decision;

        StubGateway(GatewayDecision decision) {
            this.decision = decision;
        }

        @Override
        public GatewayDecision execute(AuthorizationRequest request) {
            return decision;
        }
    }

    private static Action lowRiskAction(String id) {
        return new com.apex.engine.planner.Action() {
            @Override public String id() { return id; }
            @Override public List<Condition> preconditions() {
                return List.of();
            }
            @Override public Blackboard applyEffects(Blackboard state) {
                return state.withString("done", "true");
            }
            @Override public double cost() { return 1.0; }
            @Override public RiskLevel riskLevel() { return RiskLevel.L0; }
            @Override public ActionOutput execute(Blackboard state,
                    CancellationToken token) {
                return new ActionOutput.Success(state.withString("done", "true"));
            }
        };
    }

    @Test
    void gatewayDenyHaltsLoopWithPolicyDenied() {
        ToolGatewayPort gateway = new StubGateway(
                new GatewayDecision.Deny("blocked by policy"));
        GatePlacementPolicy gatePolicy = new GatePolicy(java.util.Set.of("read_file"));
        PlanLoop loop = new PlanLoop(
                com.apex.engine.core.PolicyEngine.permissive(),
                com.apex.engine.core.port.model.GuardrailSet.none(),
                com.apex.engine.core.port.model.InvariantSet.empty(),
                null, null, null, null,
                gateway, gatePolicy, IDENTITY);

        PlanLoopResult result = runWithLoop(loop, List.of(lowRiskAction("read_file")));

        assertThat(result.status()).isEqualTo(PlanStatus.POLICY_DENIED);
        assertThat(result.steps()).isEmpty();
    }

    @Test
    void gatewayRequireApprovalStopsLoop() {
        ToolGatewayPort gateway = new StubGateway(
                new GatewayDecision.RequireApproval(
                        new ApprovalRequest("req-1", "tool_call:run_command",
                                RiskLevel.L4)));
        GatePlacementPolicy gatePolicy = new GatePolicy(java.util.Set.of("run_command"));
        PlanLoop loop = new PlanLoop(
                com.apex.engine.core.PolicyEngine.permissive(),
                com.apex.engine.core.port.model.GuardrailSet.none(),
                com.apex.engine.core.port.model.InvariantSet.empty(),
                null, null, null, null,
                gateway, gatePolicy, IDENTITY);

        PlanLoopResult result = runWithLoop(loop, List.of(lowRiskAction("run_command")));

        assertThat(result.status()).isEqualTo(PlanStatus.APPROVAL_REQUIRED);
        assertThat(result.steps()).isEmpty();
    }

    @Test
    void gatewayApproveLetsExecutionProceed() {
        ToolGatewayPort gateway = new StubGateway(
                new GatewayDecision.Approve());
        GatePlacementPolicy gatePolicy = new GatePolicy(java.util.Set.of("read_file"));
        PlanLoop loop = new PlanLoop(
                com.apex.engine.core.PolicyEngine.permissive(),
                com.apex.engine.core.port.model.GuardrailSet.none(),
                com.apex.engine.core.port.model.InvariantSet.empty(),
                null, null, null, null,
                gateway, gatePolicy, IDENTITY);

        AtomicInteger executions = new AtomicInteger();
        Action counted = new com.apex.engine.planner.Action() {
            @Override public String id() { return "read_file"; }
            @Override public List<Condition> preconditions() {
                return List.of();
            }
            @Override public Blackboard applyEffects(Blackboard state) {
                return state.withString("done", "true");
            }
            @Override public double cost() { return 1.0; }
            @Override public RiskLevel riskLevel() { return RiskLevel.L0; }
            @Override public ActionOutput execute(Blackboard state,
                    CancellationToken token) {
                executions.incrementAndGet();
                return new ActionOutput.Success(state.withString("done", "true"));
            }
        };

        PlanLoopResult result = runWithLoop(loop, List.of(counted));

        assertThat(result.status()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(result.steps()).hasSize(1);
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void noGatewayMeansNoGating() {
        PlanLoop loop = new PlanLoop(
                com.apex.engine.core.PolicyEngine.permissive(),
                com.apex.engine.core.port.model.GuardrailSet.none(),
                com.apex.engine.core.port.model.InvariantSet.empty(),
                null, null, null, null,
                null, null, IDENTITY);

        PlanLoopResult result = runWithLoop(loop, List.of(lowRiskAction("read_file")));

        assertThat(result.status()).isEqualTo(PlanStatus.COMPLETED);
    }

    private static PlanLoopResult runWithLoop(PlanLoop loop,
                                              List<Action> actions) {
        // Bypass LoopActions.run() which constructs its own PlanLoop();
        // instead call loop.run() directly.
        com.apex.engine.planner.concurrent.CancellationToken token =
                new com.apex.engine.planner.concurrent.CancellationToken();
        com.apex.engine.core.port.ChatEventSink sink =
                com.apex.engine.core.port.ChatEventSink.NOOP;
        return loop.run(Blackboard.empty(), GOAL, actions, 5, token, sink);
    }
}
