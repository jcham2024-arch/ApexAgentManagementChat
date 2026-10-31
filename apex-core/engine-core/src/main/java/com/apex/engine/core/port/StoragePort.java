package com.apex.engine.core.port;

import com.apex.engine.core.port.model.StepLogEntry;

import java.util.List;

/**
 * Persistence for step and session logs. Tier 1 impl:
 * engine-store-sqlite (S2.5). Tier 2 impl: adapter-spring-jdbc (S6+).
 */
public interface StoragePort {
    void appendStep(StepLogEntry entry);
    List<StepLogEntry> stepsFor(String sessionId);
}
