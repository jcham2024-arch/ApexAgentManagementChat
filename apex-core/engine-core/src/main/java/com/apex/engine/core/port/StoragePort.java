package com.apex.engine.core.port;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.model.StepLogEntry;

import java.util.List;

/**
 * Persistence for step and session logs. Every read is tenant-scoped:
 * {@link #stepsFor(TenantId, String)} returns only rows whose
 * {@code tenantId} matches the argument. Tier 1 impl:
 * engine-store-sqlite (S2.5). Tier 2 impl: adapter-spring-jdbc (S6+).
 */
public interface StoragePort {
    void appendStep(StepLogEntry entry);
    List<StepLogEntry> stepsFor(TenantId tenantId, String sessionId);
}
