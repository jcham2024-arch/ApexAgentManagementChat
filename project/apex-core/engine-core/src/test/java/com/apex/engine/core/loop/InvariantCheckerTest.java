package com.apex.engine.core.loop;

import com.apex.engine.core.port.model.Invariant;
import com.apex.engine.core.port.model.InvariantSet;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.ActionOutput;
import com.apex.engine.planner.Blackboard;
import com.apex.engine.planner.concurrent.CancellationToken;
import com.apex.engine.planner.Condition;
import com.apex.engine.planner.FactCondition;
import com.apex.engine.planner.Goal;
import com.apex.engine.planner.Plan;
import com.apex.engine.planner.RiskLevel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvariantCheckerTest {

    private static final InvariantSet EMPTY = new InvariantSet(List.of());

    private static final InvariantSet REQUIRED = new InvariantSet(List.of(
            new Invariant("inv-1", "must do something"),
            new Invariant("inv-2", "must not be free")));

    private static Action noop(String id) {
        return new Action() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<Condition> preconditions() {
                return List.of();
            }

            @Override
            public Blackboard applyEffects(Blackboard state) {
                return state;
            }

            @Override
            public double cost() {
                return 1.0;
            }

            @Override
            public RiskLevel riskLevel() {
                return RiskLevel.L0;
            }

            @Override
            public ActionOutput execute(Blackboard state,
                                        CancellationToken token) {
                return new ActionOutput.Success(state);
            }
        };
    }

    @Test
    void rejectsNullPlan() {
        assertThatThrownBy(() -> InvariantChecker.check(null, EMPTY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullInvariantSet() {
        Plan plan = new Plan(List.of(noop("a")), 1.0, 1);
        assertThatThrownBy(() -> InvariantChecker.check(plan, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsEmptyPlanWhenInvariantsRequired() {
        Plan empty = new Plan(List.of(), 0.0, 0);
        assertThatThrownBy(() -> InvariantChecker.check(empty, REQUIRED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one step");
    }

    @Test
    void acceptsValidPlanWithEmptyInvariants() {
        Plan plan = new Plan(List.of(noop("a")), 1.0, 1);
        InvariantChecker.check(plan, EMPTY);
    }

    @Test
    void acceptsValidPlanWithRequiredInvariants() {
        Plan plan = new Plan(List.of(noop("a")), 1.0, 1);
        InvariantChecker.check(plan, REQUIRED);
    }

    @Test
    void rejectsNegativeCost() {
        assertThatThrownBy(() -> new Plan(List.of(noop("a")), -1.0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cost < 0");
    }

    @Test
    void rejectsNegativeDepth() {
        assertThatThrownBy(() -> new Plan(List.of(noop("a")), 1.0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("depth < 0");
    }
}
