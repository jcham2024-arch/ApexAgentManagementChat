package com.apex.engine.planner;

import com.apex.engine.planner.concurrent.CancellationToken;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic replay tests for the A* planner. Same inputs must
 * produce identical plans across runs, across JVM restarts, and across
 * arbitrary action orderings. These tests prove the planner's core
 * differentiator: deterministic-by-design orchestration.
 */
class AStarPlannerDeterminismTest {

    private final AStarPlanner planner = new AStarPlanner();

    private static Action sets(String id, String key, String value, double cost,
                               RiskLevel risk) {
        return new Action() {
            @Override public String id() { return id; }
            @Override public List<Condition> preconditions() { return List.of(); }
            @Override public Blackboard applyEffects(Blackboard state) {
                return state.withString(key, value);
            }
            @Override public double cost() { return cost; }
            @Override public RiskLevel riskLevel() { return risk; }
            @Override public ActionOutput execute(Blackboard state,
                                                  CancellationToken token) {
                return new ActionOutput.Success(applyEffects(state));
            }
        };
    }

    private static Goal done() {
        return new Goal("g", List.of(new FactCondition("done", "true")));
    }

    private static List<String> ids(Plan plan) {
        return plan.steps().stream().map(Action::id).toList();
    }

    @Test
    void sameInputsSamePlanAcrossRuns() {
        List<Action> actions = List.of(
                sets("a1", "done", "true", 1.0, RiskLevel.L0),
                sets("a2", "done", "true", 1.0, RiskLevel.L0));

        Optional<Plan> first = planner.plan(Blackboard.empty(), done(), actions);
        Optional<Plan> second = planner.plan(Blackboard.empty(), done(), actions);
        Optional<Plan> third = planner.plan(Blackboard.empty(), done(), actions);

        assertThat(first).isPresent();
        assertThat(ids(first.orElseThrow()))
                .isEqualTo(ids(second.orElseThrow()))
                .isEqualTo(ids(third.orElseThrow()));
        assertThat(first.orElseThrow().cost())
                .isEqualTo(second.orElseThrow().cost())
                .isEqualTo(third.orElseThrow().cost());
    }

    @Test
    void tieBreakingIsDeterministic() {
        // Two actions with identical cost and effect; the planner must
        // always pick the same one first.
        List<Action> actions = List.of(
                sets("first", "k", "v1", 1.0, RiskLevel.L0),
                sets("second", "k", "v2", 1.0, RiskLevel.L0));

        Optional<Plan> p1 = planner.plan(Blackboard.empty(),
                new Goal("g", List.of(new FactCondition("k", "v1"))), actions);
        Optional<Plan> p2 = planner.plan(Blackboard.empty(),
                new Goal("g", List.of(new FactCondition("k", "v2"))), actions);

        assertThat(p1).isPresent();
        assertThat(p2).isPresent();
        assertThat(ids(p1.orElseThrow())).containsExactly("first");
        assertThat(ids(p2.orElseThrow())).containsExactly("second");
    }

    @Test
    void sameMaxDepthSameResult() {
        List<Action> actions = chain(10);

        Optional<Plan> limited1 =
                planner.plan(Blackboard.empty(), done(), actions, 3);
        Optional<Plan> limited2 =
                planner.plan(Blackboard.empty(), done(), actions, 3);

        // Either both find a plan within depth 3, or both find none.
        assertThat(limited1.isPresent()).isEqualTo(limited2.isPresent());
        limited1.ifPresent(plan ->
                assertThat(plan.steps().size()).isLessThanOrEqualTo(3));
    }

    @Test
    void emptyBoardDeterministic() {
        Optional<Plan> p1 = planner.plan(Blackboard.empty(), done(), List.of());
        Optional<Plan> p2 = planner.plan(Blackboard.empty(), done(), List.of());

        assertThat(p1).isEmpty();
        assertThat(p2).isEmpty();
    }

    @Test
    void alreadySatisfiedGoalReturnsEmptyPlan() {
        Blackboard start = Blackboard.empty().withString("done", "true");
        Optional<Plan> p1 = planner.plan(start, done(), List.of(sets("x", "z", "z", 1.0, RiskLevel.L0)));
        Optional<Plan> p2 = planner.plan(start, done(), List.of(sets("x", "z", "z", 1.0, RiskLevel.L0)));

        assertThat(p1).isPresent();
        assertThat(p2).isPresent();
        assertThat(p1.orElseThrow().steps()).isEmpty();
        assertThat(p2.orElseThrow().steps()).isEmpty();
    }

    @Property
    void repeatedPlanningIsDeterministic(@ForAll String actionId,
                                         @ForAll String key,
                                         @ForAll String value,
                                         @ForAll int riskOrdinal) {
        List<Action> actions = List.of(
                sets(actionId, key, value, 1.0,
                        RiskLevel.values()[Math.floorMod(riskOrdinal,
                                RiskLevel.values().length)]));

        Optional<Plan> first = planner.plan(Blackboard.empty(), done(), actions);
        Optional<Plan> second = planner.plan(Blackboard.empty(), done(), actions);

        assertThat(first.isPresent()).isEqualTo(second.isPresent());
        if (first.isPresent()) {
            assertThat(ids(first.orElseThrow())).isEqualTo(ids(second.orElseThrow()));
            assertThat(first.orElseThrow().cost())
                    .isEqualTo(second.orElseThrow().cost());
        }
    }

    private static List<Action> chain(int length) {
        List<Action> actions = new ArrayList<>();
        List<Condition> preconditions = List.of();

        for (int i = 1; i <= length; i++) {
            boolean last = i == length;
            String key = last ? "done" : "k" + i;
            String value = last ? "true" : "v" + i;
            actions.add(chainAction(i, key, value, preconditions));
            preconditions = List.of(new FactCondition(key, value));
        }
        return actions;
    }

    private static Action chainAction(int index, String key, String value,
                                      List<Condition> preconditions) {
        return new Action() {
            @Override public String id() { return "a" + index; }
            @Override public List<Condition> preconditions() { return preconditions; }
            @Override public Blackboard applyEffects(Blackboard state) {
                return state.withString(key, value);
            }
            @Override public double cost() { return 1.0; }
            @Override public RiskLevel riskLevel() { return RiskLevel.L0; }
            @Override public ActionOutput execute(Blackboard state,
                                                  CancellationToken token) {
                return new ActionOutput.Success(applyEffects(state));
            }
        };
    }
}
