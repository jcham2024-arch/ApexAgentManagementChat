# engine-memory

**Tier:** 0 · **Purpose:** Memory retrieval and compaction.

## Allowed dependencies
java.base + jspecify only

## Ports consumed
none

## Ports implemented
none — MemoryRouter and MemoryBackend are services, not ports

## Types
- `MemoryKind` — WORKING / EPISODIC / SEMANTIC
- `MemoryQuery` — retrieval request
- `MemoryHit` — single result
- `MemoryResult` — fused result set
- `MemoryRouter` — retrieval service interface
- `MemoryBackend` — backend contract per memory kind
- `ContextCompactor` — history compaction interface
- `WorkingMemoryEntry` — single working memory entry
- `BoundedWorkingMemory` — bounded FIFO working memory backend

## Tests
`MemoryTypesTest` · `BoundedWorkingMemoryTest`
