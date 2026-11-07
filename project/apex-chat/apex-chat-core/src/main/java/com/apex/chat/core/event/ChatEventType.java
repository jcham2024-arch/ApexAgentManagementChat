package com.apex.chat.core.event;

/**
 * Type of a chat lifecycle event. The enum is closed — new event types
 * require updating this enum and all sealed implementations of
 * {@link ChatLifecycleEvent}.
 */
public enum ChatEventType {
    SESSION_OPENED,
    PLAN_STEP_STARTED,
    PLAN_STEP_COMPLETED,
    TOOL_INVOKED,
    APPROVAL_REQUESTED,
    APPROVAL_RESOLVED,
    TELEMETRY_RECORDED,
    SESSION_CLOSED,
    SESSION_FAILED
}
