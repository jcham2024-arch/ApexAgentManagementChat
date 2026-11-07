package com.apex.engine.memory;

import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.model.StepLogEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Episodic memory backend that persists entries through {@link
 * StoragePort}. Each {@link WorkingMemoryEntry} is stored as a
 * {@link StepLogEntry} with the entry's role and content encoded in
 * {@code stepType} (role + {@code \u0000} + content) and the entry's
 * timestamp in {@code durationMs}.
 *
 * <p>Tier 0: depends on {@link StoragePort} (a Tier 0 port interface).
 * The concrete {@link StoragePort} implementation (e.g.
 * {@code SqliteStoragePort}) is injected at the application level.
 */
public final class EpisodicMemoryBackend implements MemoryBackend {

    private static final String CONTENT_SEPARATOR = "\u0000";

    private final StoragePort storage;
    private final TenantId tenantId;
    private final String sessionId;
    private int nextIndex;

    public EpisodicMemoryBackend(StoragePort storage,
                                 TenantId tenantId,
                                 String sessionId) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.nextIndex = countExistingEntries();
    }

    @Override
    public MemoryKind kind() {
        return MemoryKind.EPISODIC;
    }

    @Override
    public void add(WorkingMemoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        String stepType = entry.role() + CONTENT_SEPARATOR + entry.content();
        StepLogEntry step = new StepLogEntry(
                UUID.randomUUID().toString(),
                tenantId,
                sessionId,
                nextIndex++,
                stepType,
                entry.at().toEpochMilli());
        storage.appendStep(step);
    }

    @Override
    public MemoryResult query(MemoryQuery query) {
        Objects.requireNonNull(query, "query");

        Set<MemoryKind> kinds = query.kinds();
        if (!kinds.contains(kind())) {
            return new MemoryResult(List.of(), Set.of());
        }

        List<StepLogEntry> steps = storage.stepsFor(tenantId, sessionId);
        String text = query.text().toLowerCase(Locale.ROOT);
        List<MemoryHit> hits = new ArrayList<>();

        for (StepLogEntry step : steps) {
            String stepType = step.stepType();
            int sepIdx = stepType.indexOf(CONTENT_SEPARATOR);
            if (sepIdx < 0) {
                continue;
            }

            String role = stepType.substring(0, sepIdx);
            String content = stepType.substring(sepIdx + 1);

            double score;
            if (content.contains(query.text())) {
                score = 1.0;
            } else if (content.toLowerCase(Locale.ROOT).contains(text)) {
                score = 0.8;
            } else {
                continue;
            }

            hits.add(new MemoryHit(kind(), role, content, score));
        }

        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        int maxHits = Math.min(hits.size(), query.maxHits());
        List<MemoryHit> capped = hits.subList(0, maxHits);

        return new MemoryResult(capped, Set.of(kind()));
    }

    @Override
    public int size() {
        return nextIndex;
    }

    @Override
    public synchronized void clear() {
        storage.clear(tenantId, sessionId);
        nextIndex = 0;
    }

    private int countExistingEntries() {
        return storage.stepsFor(tenantId, sessionId).size();
    }
}
