package com.apex.engine.core.port;

import com.apex.engine.core.port.model.TelemetrySpan;

/**
 * Observability spans. Tier 1 impl: no-op (S2). Tier 2 impl:
 * adapter-micrometer (S6+). First impl lands in S2 with a no-op.
 */
public interface TelemetryPort {
    void record(TelemetrySpan span);
}
