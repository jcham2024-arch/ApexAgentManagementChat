package com.apex.engine.planner;

/**
 * Result of executing an {@link Action}. Success carries the new
 * blackboard state; Failure carries the reason. Executing the same
 * action against the same state must produce the same output
 * (determinism).
 */
public sealed interface ActionOutput {
    record Success(Blackboard newState) implements ActionOutput {}
    record Failure(String reason) implements ActionOutput {}
}
