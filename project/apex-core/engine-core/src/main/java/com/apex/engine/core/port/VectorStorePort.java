package com.apex.engine.core.port;

import com.apex.engine.core.port.model.VectorHit;

import java.util.List;

/**
 * Semantic retrieval over embeddings. Tier 1 impl:
 * engine-store-sqlite (S4). Tier 2 impl: adapter-spring-ai (pgvector,
 * S4). First impl lands in S4.
 */
public interface VectorStorePort {
    void store(String id, float[] vector);
    List<VectorHit> search(float[] query, int k);
}
