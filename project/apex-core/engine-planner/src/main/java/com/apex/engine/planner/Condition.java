package com.apex.engine.planner;

/**
 * A predicate over {@link Blackboard} state. Implementations must have
 * value-based {@code equals} and {@code hashCode} so that two
 * conditions describing the same fact are treated as equal.
 */
public interface Condition {
    boolean evaluate(Blackboard state);
    String describe();
}
