package com.apex.engine.core.loop;

import com.apex.engine.planner.ActionOutput;

import java.util.Objects;

/**
 * A single step executed by {@link PlanLoop}. Used for logging and
 * later translation to the persistent step_log.
 */
public record StepRecord(int index, String actionId, ActionOutput output) {
    public StepRecord {
        if (index < 0) throw new IllegalArgumentException("index < 0");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(output, "output");
    }
}
