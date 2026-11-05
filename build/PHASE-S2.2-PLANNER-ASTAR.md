# PHASE-S2.2-PLANNER-ASTAR.md — Session Package, Part 3 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 3 of 5:** §4.1, blackboard default, A* planner, package-info.
Parts: SESSION (§0–§3) · PRIMITIVES (§4.1a) · PLANNER (§4.1b) · LOOP
(§4.2) · TESTS-DOCS (§4.3–§7), each `PHASE-S2.2-PLANNER-<part>.md`. Cut
for the 200-line rule in `AGENT_RULES.md` §2.13.

---
**`Blackboard.java`**
```java
package com.apex.engine.planner;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of the planner's world state. Every mutating
 * method returns a new instance. Keys are strings; values are typed
 * and retrieved via {@link #get(String, Class)}.
 */
public interface Blackboard {
    Optional<String> getString(String key);
    <T> Optional<T> get(String key, Class<T> type);
    Blackboard withString(String key, String value);
    Blackboard with(String key, Object value);
    Map<String, Object> snapshot();
}
```

**`ImmutableBlackboard.java`**
```java
package com.apex.engine.planner;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link Blackboard} backed by an unmodifiable map. Equality is
 * value-based (record semantics on the wrapped map).
 */
public record ImmutableBlackboard(Map<String, Object> facts) implements Blackboard {
    public ImmutableBlackboard {
        Objects.requireNonNull(facts, "facts");
        facts = Map.copyOf(facts);
    }

    public static ImmutableBlackboard empty() {
        return new ImmutableBlackboard(Map.of());
    }

    @Override
    public Optional<String> getString(String key) {
        return get(key, String.class);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object v = facts.get(key);
        return v != null && type.isInstance(v) ? Optional.of(type.cast(v)) : Optional.empty();
    }

    @Override
    public Blackboard withString(String key, String value) {
        return with(key, value);
    }

    @Override
    public Blackboard with(String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String, Object> next = new HashMap<>(facts);
        next.put(key, value);
        return new ImmutableBlackboard(next);
    }

    @Override
    public Map<String, Object> snapshot() {
        return facts;
    }
}
```

**`AStarPlanner.java`**
```java
package com.apex.engine.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* search over typed actions. Deterministic: identical inputs
 * produce identical plans. Uses an admissible heuristic (count of
 * unsatisfied goal conditions). Costs include risk penalties.
 *
 * <p>The state space is explored in best-first order by {@code fScore}.
 * Ties are broken by insertion order to guarantee determinism.
 */
public final class AStarPlanner {

    private static final int DEFAULT_MAX_DEPTH = 32;

    public Optional<Plan> plan(Blackboard initial, Goal goal, List<Action> actions) {
        return plan(initial, goal, actions, DEFAULT_MAX_DEPTH);
    }

    public Optional<Plan> plan(Blackboard initial, Goal goal,
                               List<Action> actions, int maxDepth) {
        if (goal.satisfiedBy(initial)) {
            return Optional.of(new Plan(List.of(), 0.0, 0));
        }

        long sequence = 0L;
        PriorityQueue<Node> open = new PriorityQueue<>(
                Comparator.<Node>comparingDouble(Node::fScore)
                          .thenComparingLong(Node::sequence));

        Set<Blackboard> seen = new HashSet<>();
        open.add(new Node(initial, List.of(), 0.0,
                heuristic(initial, goal), sequence++));

        while (!open.isEmpty()) {
            Node node = open.poll();

            if (node.depth() > maxDepth) continue;
            if (!seen.add(node.state())) continue;
            if (goal.satisfiedBy(node.state())) {
                return Optional.of(new Plan(node.actions(), node.gCost(), node.depth()));
            }

            for (Action action : actions) {
                if (!applicable(action, node.state())) continue;

                Blackboard next = action.applyEffects(node.state());
                if (next.equals(node.state())) continue;

                double stepCost = action.cost() + action.riskLevel().penalty();
                double gCost = node.gCost() + stepCost;
                double hCost = heuristic(next, goal);
                List<Action> path = new ArrayList<>(node.actions());
                path.add(action);

                open.add(new Node(next, List.copyOf(path), gCost, hCost,
                        sequence++));
            }
        }
        return Optional.empty();
    }

    private static boolean applicable(Action action, Blackboard state) {
        return action.preconditions().stream().allMatch(c -> c.evaluate(state));
    }

    private static double heuristic(Blackboard state, Goal goal) {
        return goal.unsatisfiedCount(state);
    }

    private record Node(Blackboard state, List<Action> actions,
                        double gCost, double hCost, long sequence) {
        double fScore() {
            return gCost + hCost;
        }
        int depth() {
            return actions.size();
        }
    }
}
```

**`package-info.java`** (replace content)
```java
/**
 * Pure A* planner with GOAP preconditions and effects, typed
 * blackboard, and risk-weighted costs. Tier 0. No ports. Bridging to
 * PlannerPort happens in engine-core.
 */
@NullMarked
package com.apex.engine.planner;

import org.jspecify.annotations.NullMarked;
```
