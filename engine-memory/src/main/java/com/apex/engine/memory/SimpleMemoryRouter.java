package com.apex.engine.memory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Default {@link MemoryRouter} implementation. Consults each backend
 * whose {@link MemoryBackend#kind()} is in {@link MemoryQuery#kinds()},
 * collects hits, deduplicates by {@code (kind, source, content)}, sorts
 * by descending score, and caps at {@link MemoryQuery#maxHits()}.
 *
 * <p>Returns a stable, deterministic result for the same query and
 * backends. Backends that throw are skipped; their kind is not added to
 * {@code queried}.
 */
public final class SimpleMemoryRouter implements MemoryRouter {

    private final List<MemoryBackend> backends;

    public SimpleMemoryRouter(List<MemoryBackend> backends) {
        Objects.requireNonNull(backends, "backends");
        this.backends = List.copyOf(backends);
    }

    @Override
    public MemoryResult route(MemoryQuery query) {
        Objects.requireNonNull(query, "query");

        Set<MemoryKind> requestedKinds = query.kinds();
        List<MemoryHit> hits = new ArrayList<>();
        Set<MemoryKind> queried = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();

        for (MemoryBackend backend : backends) {
            MemoryKind kind = backend.kind();
            if (!requestedKinds.contains(kind)) {
                continue;
            }
            try {
                MemoryResult result = backend.query(query);
                queried.addAll(result.queried());
                for (MemoryHit hit : result.hits()) {
                    String key = kind + "|" + hit.source() + "|" + hit.content();
                    if (seen.add(key)) {
                        hits.add(hit);
                    }
                }
            } catch (RuntimeException e) {
                // Skip failing backends; do not add their kind to queried.
            }
        }

        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        int maxHits = Math.min(hits.size(), query.maxHits());
        List<MemoryHit> capped = hits.subList(0, maxHits);

        return new MemoryResult(capped, Set.copyOf(queried));
    }
}
