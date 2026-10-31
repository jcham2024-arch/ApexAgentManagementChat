package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Objects;

/**
 * A planning request for one goal. The action list is the closed set
 * the planner may choose from.
 */
public record PlanRequest(String goalId, List<String> availableActionIds) {
    public PlanRequest {
        Objects.requireNonNull(goalId, "goalId");
        availableActionIds = List.copyOf(availableActionIds);
    }
}
