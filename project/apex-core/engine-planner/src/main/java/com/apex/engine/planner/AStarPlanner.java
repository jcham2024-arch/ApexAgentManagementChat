package com.apex.engine.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
