package com.apex.engine.memory;

/**
 * Retrieves memory hits across one or more backends, fuses them by
 * relevance, and returns up to {@code query.maxHits()}. Implementations
 * select which backends to consult based on {@code query.kinds()}, skip
 * unavailable backends, and return a stable, deterministic result for
 * the same query and backends. Tier 0.
 */
public interface MemoryRouter {
    MemoryResult route(MemoryQuery query);
}
