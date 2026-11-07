package com.apex.engine.memory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded FIFO working memory. Stores at most {@code maxEntries}
 * entries; the oldest is evicted when the buffer overflows. Query is
 * case-insensitive substring match with tiered scoring:
 *
 * <ul>
 *   <li>exact case-sensitive substring → score 1.0</li>
 *   <li>case-insensitive substring → score 0.8</li>
 *   <li>no match → not a hit</li>
 * </ul>
 *
 * <p>All public methods are {@code synchronized}. Concurrent appends
 * and reads from multiple {@code RealTaskRunner} instances sharing one
 * backend are safe.
 */
public final class BoundedWorkingMemory implements MemoryBackend {

    private static final double EXACT_SCORE = 1.0;
    private static final double SUBSTRING_SCORE = 0.8;

    private final int maxEntries;
    private final Deque<WorkingMemoryEntry> entries;

    public BoundedWorkingMemory(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        this.entries = new ArrayDeque<>(maxEntries);
    }

    /** Appends an entry with the current wall-clock time. */
    public synchronized void add(String role, String content) {
        add(role, content, Instant.now());
    }

    /** Appends an entry with an explicit timestamp (for tests). */
    public synchronized void add(String role, String content, Instant at) {
        WorkingMemoryEntry entry = new WorkingMemoryEntry(role, content, at);
        if (entries.size() >= maxEntries) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }

    @Override
    public synchronized void add(WorkingMemoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        add(entry.role(), entry.content(), entry.at());
    }

    /** Returns an immutable snapshot of the current entries, oldest first. */
    public synchronized List<WorkingMemoryEntry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public synchronized int size() {
        return entries.size();
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    @Override
    public synchronized MemoryResult query(MemoryQuery query) {
        Objects.requireNonNull(query, "query");

        Set<MemoryKind> kinds = query.kinds();
        if (!kinds.contains(kind())) {
            return new MemoryResult(List.of(), Set.of());
        }

        String text = query.text().toLowerCase(Locale.ROOT);
        List<MemoryHit> hits = new ArrayList<>();
        for (WorkingMemoryEntry entry : entries) {
            String content = entry.content();
            double score;
            if (content.contains(query.text())) {
                score = EXACT_SCORE;
            } else if (content.toLowerCase(Locale.ROOT).contains(text)) {
                score = SUBSTRING_SCORE;
            } else {
                continue;
            }
            hits.add(new MemoryHit(kind(), entry.role(), content, score));
        }

        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        int maxHits = Math.min(hits.size(), query.maxHits());
        List<MemoryHit> capped = hits.subList(0, maxHits);

        return new MemoryResult(capped, Set.of(kind()));
    }

    @Override
    public MemoryKind kind() {
        return MemoryKind.WORKING;
    }
}
