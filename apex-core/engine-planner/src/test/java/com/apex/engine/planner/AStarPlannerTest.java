package com.apex.engine.planner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.apex.engine.planner.concurrent.CancellationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AStarPlannerTest {

    private final AStarPlanner planner = new AStarPlanner();

    private record StubAction(String id, List<Condition> preconditions,
                              String key, String value, double cost,
                              RiskLevel riskLevel) implements Action {

        @Override
        public Blackboard applyEffects(Blackboard state) {
            return state.withString(key, value);
        }

        @Override
        public ActionOutput execute(Blackboard state, CancellationToken token) {
            return new ActionOutput.Success(applyEffects(state));
        }
    }

    private static StubAction sets(String id, String key, String value) {
        return new StubAction(id, List.of(), key, value, 1.0, RiskLevel.L0);
    }

    private static Goal done() {
        return new Goal("g", List.of(new FactCondition("done", "true")));
    }

    private static List<String> ids(Plan plan) {
        return plan.steps().stream().map(Action::id).toList();
    }

    @Test
    void determinism() {
        List<Action> actions = List.of(sets("a1", "done", "true"),
                sets("a2", "done", "true"));

        Optional<Plan> first = planner.plan(Blackboard.empty(), done(), actions);
        Optional<Plan> second = planner.plan(Blackboard.empty(), done(), actions);

        assertThat(first).isPresent();
        assertThat(ids(first.orElseThrow())).isEqualTo(ids(second.orElseThrow()));
        assertThat(first.orElseThrow().cost()).isEqualTo(second.orElseThrow().cost());
        assertThat(first.orElseThrow().depth()).isEqualTo(second.orElseThrow().depth());
    }

    @Test
    void goalAlreadySatisfiedYieldsEmptyPlan() {
        Blackboard initial = Blackboard.empty().withString("done", "true");

        Plan plan = planner.plan(initial, done(), List.of(sets("a1", "k", "v")))
                .orElseThrow();

        assertThat(plan.steps()).isEmpty();
        assertThat(plan.cost()).isZero();
        assertThat(plan.depth()).isZero();
    }

    @Test
    void singleActionPath() {
        Plan plan = planner.plan(Blackboard.empty(), done(),
                List.of(sets("a1", "done", "true"))).orElseThrow();

        assertThat(ids(plan)).containsExactly("a1");
    }

    @Test
    void twoActionPath() {
        Action second = new StubAction("a2",
                List.of(new FactCondition("k1", "v1")), "done", "true", 1.0,
                RiskLevel.L0);

        Plan plan = planner.plan(Blackboard.empty(), done(),
                List.of(sets("a1", "k1", "v1"), second)).orElseThrow();

        assertThat(ids(plan)).containsExactly("a1", "a2");
    }

    @Test
    void noPlanPossible() {
        assertThat(planner.plan(Blackboard.empty(), done(),
                List.of(sets("a1", "other", "x")))).isEmpty();
    }

    @Test
    void prefersLowerRiskPathAtEqualBaseCost() {
        Action risky = new StubAction("risky", List.of(), "done", "true", 1.0,
                RiskLevel.L4);
        Action safe = new StubAction("safe", List.of(), "done", "true", 1.0,
                RiskLevel.L1);

        Plan plan = planner.plan(Blackboard.empty(), done(),
                List.of(risky, safe)).orElseThrow();

        assertThat(ids(plan)).containsExactly("safe");
    }

    @Test
    void costIncludesBaseCostAndRiskPenalty() {
        Action a1 = new StubAction("a1", List.of(), "k1", "v1", 1.0, RiskLevel.L1);
        Action a2 = new StubAction("a2", List.of(new FactCondition("k1", "v1")),
                "k2", "v2", 2.0, RiskLevel.L2);
        Action a3 = new StubAction("a3", List.of(new FactCondition("k2", "v2")),
                "done", "true", 3.0, RiskLevel.L0);

        Plan plan = planner.plan(Blackboard.empty(), done(),
                List.of(a1, a2, a3)).orElseThrow();

        assertThat(ids(plan)).containsExactly("a1", "a2", "a3");
        assertThat(plan.cost()).isCloseTo(6.4, within(1.0e-9));
    }

    @Test
    void depthLimitStopsSearch() {
        List<Action> actions = chain(5);

        assertThat(planner.plan(Blackboard.empty(), done(), actions, 3))
                .isEmpty();
    }

    @Test
    void plannerIsBoundedByMaxDepth() {
        // maxDepth acts as the planner's cancellation mechanism: the
        // search stops after the given depth rather than searching
        // indefinitely.
        List<Action> actions = chain(10);
        Optional<Plan> result = planner.plan(Blackboard.empty(),
                done(), actions, 3);

        // Either a plan was found within 3 steps, or none was.
        // Either way, the planner terminated (no infinite loop).
        result.ifPresent(plan ->
                assertThat(plan.steps().size()).isLessThanOrEqualTo(3));
    }

    private static List<Action> chain(int length) {
        List<Action> actions = new ArrayList<>();
        List<Condition> preconditions = List.of();

        for (int i = 1; i <= length; i++) {
            boolean last = i == length;
            String key = last ? "done" : "k" + i;
            String value = last ? "true" : "v" + i;
            actions.add(new StubAction("a" + i, preconditions, key, value, 1.0,
                    RiskLevel.L0));
            preconditions = List.of(new FactCondition(key, value));
        }
        return actions;
    }
}
