# engine-memory

**Tier:** 0 · **Purpose:** Memory retrieval and compaction.

## Allowed dependencies
java.base + jspecify only

## Ports consumed
none in S4.0 — S4.2 wraps StoragePort, S4.3 wraps VectorStorePort

## Ports implemented
none — MemoryRouter is a service, not a port

## Types
- `MemoryKind` — WORKING / EPISODIC / SEMANTIC
- `MemoryQuery` — retrieval request
- `MemoryHit` — single result
- `MemoryResult` — fused result set
- `MemoryRouter` — retrieval service interface
- `ContextCompactor` — history compaction interface

## Tests
`MemoryTypesTest`
