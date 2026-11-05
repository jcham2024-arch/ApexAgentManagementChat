package com.apex.engine.memory;

/**
 * Backend contract for a memory kind (WORKING, EPISODIC, SEMANTIC).
 * A backend stores entries and answers retrieval queries scoped to its
 * kind. Router (S4.4) consults one or more backends per {@link
 * MemoryQuery} and fuses results. Tier 0.
 */
public interface MemoryBackend {

    /**
     * Returns the kind of memory this backend serves.
     */
    MemoryKind kind();

    /**
     * Stores an entry. Implementations may evict older entries if the
     * backend is bounded.
     */
    void add(WorkingMemoryEntry entry);

    /**
     * Answers a retrieval query. Returns hits matching this backend's
     * kind, capped at {@code query.maxHits()}. Implementations skip
     * backends whose kind is not in {@code query.kinds()} — the router
     * handles that filtering, but backends must be robust to it.
     */
    MemoryResult query(MemoryQuery query);

    /**
     * Returns the number of entries currently stored.
     */
    int size();

    /**
     * Removes all entries from this backend.
     */
    void clear();
}
