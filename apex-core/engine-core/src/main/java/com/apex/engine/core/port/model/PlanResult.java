package com.apex.engine.core.port.model;

import java.util.List;

/**
 * The planner's ordered action choice. Order is the execution order.
 */
public record PlanResult(List<String> chosenActionIds) {
    public PlanResult {
        chosenActionIds = List.copyOf(chosenActionIds);
    }
}
