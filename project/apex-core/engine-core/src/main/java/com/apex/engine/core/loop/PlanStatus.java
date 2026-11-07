package com.apex.engine.core.loop;

/**
 * Terminal status of a {@link PlanLoop} run.
 */
public enum PlanStatus {
    COMPLETED,
    STUCK,
    FAILED,
    CANCELLED,
    MAX_STEPS_EXCEEDED
}
