package com.apex.engine.core.loop;

import com.apex.engine.core.port.AuditPort;
import com.apex.engine.core.port.ChatEventSink;
import com.apex.engine.core.port.CheckpointPort;
import com.apex.engine.core.port.model.AuditEntry;
import com.apex.engine.core.port.model.CheckpointRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory test doubles for the {@code PlanLoop} tests. Each one records
 * calls so a test can assert on what the loop emitted or persisted.
 */
final class LoopTestDoubles {

    private LoopTestDoubles() {
    }

    /** Captures event types and sequences emitted by the loop. */
    static final class CapturingSink implements ChatEventSink {
        final List<String> types = new ArrayList<>();
        final List<Long> sequences = new ArrayList<>();

        @Override
        public void emit(String type, long sequence, Instant at) {
            types.add(type);
            sequences.add(sequence);
        }
    }

    /** In-memory audit port recording every appended entry. */
    static final class FakeAuditPort implements AuditPort {
        final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public AuditEntry append(String action, Instant at, String traceId,
                                 String spanId) {
            AuditEntry entry = new AuditEntry("e", action, at, "", "h",
                    traceId, spanId);
            entries.add(entry);
            return entry;
        }

        @Override
        public List<AuditEntry> all() {
            return List.copyOf(entries);
        }

        @Override
        public boolean verifyChain() {
            return true;
        }
    }

    /** In-memory checkpoint port keyed by session id. */
    static final class FakeCheckpointPort implements CheckpointPort {
        private final Map<String, CheckpointRecord> store = new HashMap<>();

        @Override
        public void saveCheckpoint(String sessionId, int stepIndex,
                                   String stateSnapshot) {
            store.put(sessionId, new CheckpointRecord(sessionId, stepIndex,
                    stateSnapshot, Instant.now()));
        }

        @Override
        public Optional<CheckpointRecord> restoreCheckpoint(String sessionId) {
            return Optional.ofNullable(store.get(sessionId));
        }

        @Override
        public void deleteCheckpoint(String sessionId) {
            store.remove(sessionId);
        }

        List<CheckpointRecord> all() {
            return List.copyOf(store.values());
        }
    }
}
