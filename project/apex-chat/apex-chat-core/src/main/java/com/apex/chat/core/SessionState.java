package com.apex.chat.core;

/**
 * Lifecycle state of a chat session. Transitions are monotonic:
 * {@code OPEN} → {@code CLOSED} or {@code OPEN} → {@code FAILED}.
 */
public enum SessionState {
    OPEN,
    CLOSED,
    FAILED
}
