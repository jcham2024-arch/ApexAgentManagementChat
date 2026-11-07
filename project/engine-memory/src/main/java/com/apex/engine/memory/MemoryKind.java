package com.apex.engine.memory;

/**
 * Isolation tier for memory retrieval. WORKING is short-term
 * conversation state, EPISODIC is turn-level context, SEMANTIC is
 * long-term knowledge.
 */
public enum MemoryKind {
    WORKING,
    EPISODIC,
    SEMANTIC
}
